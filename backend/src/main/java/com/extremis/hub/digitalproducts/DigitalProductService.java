package com.extremis.hub.digitalproducts;

import com.extremis.hub.admin.ForbiddenException;
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
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** entitlementKey = productId.toString() for source="product-purchase" rows -- see UserEntitlement. */
@Service
@RequiredArgsConstructor
public class DigitalProductService {

    private static final String PRODUCT_PURCHASE_SOURCE = "product-purchase";

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final UserEntitlementRepository userEntitlementRepository;
    private final UserRepository userRepository;
    private final DownloadTokenService downloadTokenService;
    private final DigitalProductProperties properties;

    public List<PublicProductResponse> listActive() {
        return productRepository.findAll().stream()
            .filter(p -> p.isActive() && p.getDeletedAt() == null)
            .map(p -> PublicProductResponse.builder()
                .id(p.getId())
                .categorySlug(p.getCategory().getSlug())
                .name(p.getName())
                .priceCents(p.getPriceCents())
                .currency(p.getCurrency())
                .build())
            .toList();
    }

    @Transactional
    public TestPurchaseResponse testPurchase(UUID userId, UUID productId) {
        if (!properties.isTestPurchasesEnabled()) {
            throw new BusinessRuleViolationException("Test-mode purchases are currently disabled.");
        }

        Product product = productRepository.findById(productId)
            .filter(p -> p.isActive() && p.getDeletedAt() == null)
            .orElseThrow(() -> new ResourceNotFoundException("Product not found."));

        String entitlementKey = productId.toString();
        if (userEntitlementRepository.findByUserIdAndEntitlementKey(userId, entitlementKey).isPresent()) {
            throw new BusinessRuleViolationException(
                "You already own this product -- check your dashboard to download it.");
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found."));

        Order order = new Order();
        order.setUser(user);
        order.setStatus(OrderStatus.PAID);
        order.setTotalCents(product.getPriceCents());
        order.setCurrency(product.getCurrency());
        orderRepository.save(order);

        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(product);
        item.setPriceCents(product.getPriceCents());
        item.setQuantity(1);
        orderItemRepository.save(item);

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setProvider("test");
        payment.setStatus("succeeded");
        payment.setVerifiedAt(Instant.now());
        paymentRepository.save(payment);

        UserEntitlement entitlement = new UserEntitlement();
        entitlement.setUser(user);
        entitlement.setEntitlementKey(entitlementKey);
        entitlement.setSource(PRODUCT_PURCHASE_SOURCE);
        entitlement.setGrantedAt(Instant.now());
        userEntitlementRepository.save(entitlement);

        return new TestPurchaseResponse(order.getId());
    }

    /** Throws ForbiddenException if the user has no entitlement for this product. */
    public String generateDownloadUrl(UUID userId, UUID productId) {
        userEntitlementRepository.findByUserIdAndEntitlementKey(userId, productId.toString())
            .orElseThrow(() -> new ForbiddenException("You don't own this product."));
        String token = downloadTokenService.issueToken(productId);
        return "/api/v1/downloads/" + token;
    }

    public List<UUID> listOwnedProductIds(UUID userId) {
        return userEntitlementRepository.findAllByUserIdAndSource(userId, PRODUCT_PURCHASE_SOURCE).stream()
            .map(e -> UUID.fromString(e.getEntitlementKey()))
            .toList();
    }
}
