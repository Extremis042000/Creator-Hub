package com.extremis.hub.payment;

import com.extremis.hub.config.CorsProperties;
import com.extremis.hub.domain.Order;
import com.extremis.hub.domain.OrderItem;
import com.extremis.hub.domain.OrderStatus;
import com.extremis.hub.domain.Payment;
import com.extremis.hub.domain.Product;
import com.extremis.hub.domain.User;
import com.extremis.hub.domain.UserEntitlement;
import com.extremis.hub.repository.OrderItemRepository;
import com.extremis.hub.repository.OrderRepository;
import com.extremis.hub.repository.PaymentRepository;
import com.extremis.hub.repository.ProductRepository;
import com.extremis.hub.repository.UserEntitlementRepository;
import com.extremis.hub.repository.UserRepository;
import com.extremis.hub.web.BusinessRuleViolationException;
import com.extremis.hub.web.ResourceNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Real checkout, gated entirely on a PaymentProvider bean existing --
 * see PaymentProvider's javadoc. Until the founder chooses a provider
 * and completes KYC (docs/05-founder-action-checklist.md), createSession
 * fails with a clear, actionable error and the free test-mode purchase
 * built in Phase 19 (DigitalProductService) remains the only working
 * purchase path -- that's intentional, not a bug.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckoutService {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final UserEntitlementRepository userEntitlementRepository;
    private final UserRepository userRepository;
    private final CorsProperties corsProperties;
    private final Optional<PaymentProvider> paymentProvider;

    @Transactional
    public CheckoutSessionResponse createSession(UUID userId, UUID productId) {
        PaymentProvider provider = paymentProvider.orElseThrow(() -> new BusinessRuleViolationException(
            "Real payments aren't set up yet -- use the free test-mode purchase on /store for now."));

        Product product = productRepository.findById(productId)
            .filter(p -> p.isActive() && p.getDeletedAt() == null)
            .orElseThrow(() -> new ResourceNotFoundException("Product not found."));

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found."));

        Order order = new Order();
        order.setUser(user);
        order.setStatus(OrderStatus.PENDING);
        order.setTotalCents(product.getPriceCents());
        order.setCurrency(product.getCurrency());
        orderRepository.save(order);

        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(product);
        item.setPriceCents(product.getPriceCents());
        item.setQuantity(1);
        orderItemRepository.save(item);

        String frontendBaseUrl = corsProperties.getAllowedOrigins().isEmpty()
            ? "" : corsProperties.getAllowedOrigins().get(0);
        String successUrl = frontendBaseUrl + "/store/success?orderId=" + order.getId();
        String cancelUrl = frontendBaseUrl + "/store";

        CheckoutSessionResult result = provider.createCheckoutSession(order, successUrl, cancelUrl);
        return new CheckoutSessionResponse(provider.getProviderName(), result.providerSessionId(), result.redirectUrl());
    }

    /**
     * Idempotent: a Payment row already existing for this order means
     * a previous delivery of the same webhook was already processed
     * (providers routinely retry/redeliver) -- return silently rather
     * than double-granting entitlements or hitting Payment.order_id's
     * unique constraint.
     */
    @Transactional
    public void handleWebhook(String rawPayload, Map<String, String> headers) {
        PaymentProvider provider = paymentProvider.orElseThrow(
            () -> new BusinessRuleViolationException("No payment provider configured."));

        PaymentWebhookEvent event = provider.verifyAndParseWebhook(rawPayload, headers);

        Order order = orderRepository.findById(event.orderId())
            .orElseThrow(() -> new ResourceNotFoundException("Order not found for webhook."));

        if (paymentRepository.findByOrderId(order.getId()).isPresent()) {
            log.info("Ignoring duplicate webhook delivery for order {}", order.getId());
            return;
        }

        if (!event.successful()) {
            order.setStatus(OrderStatus.FAILED);
            orderRepository.save(order);
            return;
        }

        order.setStatus(OrderStatus.PAID);
        orderRepository.save(order);

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setProvider(provider.getProviderName());
        payment.setProviderRef(event.providerPaymentRef());
        payment.setStatus("succeeded");
        payment.setVerifiedAt(Instant.now());
        paymentRepository.save(payment);

        List<OrderItem> items = orderItemRepository.findAllByOrderId(order.getId());
        for (OrderItem item : items) {
            UserEntitlement entitlement = new UserEntitlement();
            entitlement.setUser(order.getUser());
            entitlement.setEntitlementKey(item.getProduct().getId().toString());
            entitlement.setSource("product-purchase");
            entitlement.setGrantedAt(Instant.now());
            userEntitlementRepository.save(entitlement);
        }
    }
}
