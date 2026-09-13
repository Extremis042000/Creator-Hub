package com.extremis.hub.payment.cashfree;

import com.extremis.hub.domain.Order;
import com.extremis.hub.payment.CheckoutSessionResult;
import com.extremis.hub.payment.PaymentProvider;
import com.extremis.hub.payment.PaymentWebhookEvent;
import com.extremis.hub.web.BusinessRuleViolationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Verified against Cashfree's own published API docs (Create Order,
 * webhook signature verification, PAYMENT_SUCCESS_WEBHOOK payload
 * shape) as of 2026-09 -- NOT yet exercised against a real Cashfree
 * account: no sandbox credentials exist yet (waiting on founder KYC),
 * and the webhook endpoint isn't reachable from Cashfree's servers
 * without a public tunnel (e.g. ngrok) during local testing. Treat
 * this as "correct per the documented contract, unverified live"
 * until both of those are in place.
 *
 * Known gap: Cashfree requires customer_phone on order creation; the
 * app collects no phone number today (Google OAuth gives only email),
 * so a placeholder is sent. A real checkout flow needs to either
 * collect a phone number first or this will need revisiting once
 * that's built.
 */
public class CashfreePaymentProvider implements PaymentProvider {

    private static final String PLACEHOLDER_PHONE = "9999999999";

    private final CashfreeProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();

    public CashfreePaymentProvider(CashfreeProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getProviderName() {
        return "cashfree";
    }

    @Override
    public CheckoutSessionResult createCheckoutSession(Order order, String successUrl, String cancelUrl) {
        Map<String, Object> customerDetails = new LinkedHashMap<>();
        customerDetails.put("customer_id", order.getUser().getId().toString());
        customerDetails.put("customer_email", order.getUser().getEmail());
        // TODO see class javadoc -- no real phone number exists to send yet.
        customerDetails.put("customer_phone", PLACEHOLDER_PHONE);

        Map<String, Object> orderMeta = new LinkedHashMap<>();
        // Cashfree has one return_url for both outcomes (not a separate
        // cancel_url) -- the page at successUrl is expected to check the
        // resulting order status itself. cancelUrl is unused for Cashfree.
        orderMeta.put("return_url", successUrl);
        orderMeta.put("notify_url", properties.getNotifyUrl());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("order_id", order.getId().toString());
        body.put("order_amount", order.getTotalCents() / 100.0);
        body.put("order_currency", order.getCurrency());
        body.put("customer_details", customerDetails);
        body.put("order_meta", orderMeta);

        CashfreeOrderResponse response = restClient.post()
            .uri(baseUrl() + "/orders")
            .header("x-client-id", properties.getClientId())
            .header("x-client-secret", properties.getClientSecret())
            .header("x-api-version", properties.getApiVersion())
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(CashfreeOrderResponse.class);

        if (response == null || response.paymentSessionId() == null) {
            throw new BusinessRuleViolationException(
                "Cashfree did not return a payment session -- checkout could not be started.");
        }

        // No redirectUrl: Cashfree's checkout is driven client-side by
        // its JS SDK using paymentSessionId, not a server-issued redirect.
        return new CheckoutSessionResult(response.paymentSessionId(), null);
    }

    @Override
    public PaymentWebhookEvent verifyAndParseWebhook(String rawPayload, Map<String, String> headers) {
        String signature = header(headers, "x-webhook-signature");
        String timestamp = header(headers, "x-webhook-timestamp");
        if (signature == null || timestamp == null) {
            throw new BusinessRuleViolationException("Missing Cashfree webhook signature headers.");
        }

        String expectedSignature = computeSignature(timestamp, rawPayload);
        if (!MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessRuleViolationException("Cashfree webhook signature verification failed.");
        }

        CashfreeWebhookPayload payload;
        try {
            payload = objectMapper.readValue(rawPayload, CashfreeWebhookPayload.class);
        } catch (Exception e) {
            throw new BusinessRuleViolationException("Malformed Cashfree webhook payload.");
        }

        if (payload.data() == null || payload.data().order() == null || payload.data().order().orderId() == null) {
            throw new BusinessRuleViolationException("Cashfree webhook payload is missing order_id.");
        }

        boolean successful = "PAYMENT_SUCCESS_WEBHOOK".equals(payload.type())
            && payload.data().payment() != null
            && "SUCCESS".equalsIgnoreCase(payload.data().payment().paymentStatus());

        UUID orderId = UUID.fromString(payload.data().order().orderId());
        String paymentRef = payload.data().payment() != null ? payload.data().payment().cfPaymentId() : null;

        return new PaymentWebhookEvent(orderId, successful, paymentRef);
    }

    /** Base64Encode(HMACSHA256(timestamp + rawBody, clientSecret)) -- per Cashfree's documented scheme. */
    private String computeSignature(String timestamp, String rawBody) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getClientSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signature = mac.doFinal((timestamp + rawBody).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute Cashfree webhook signature", e);
        }
    }

    private String baseUrl() {
        return properties.isSandbox() ? "https://sandbox.cashfree.com/pg" : "https://api.cashfree.com/pg";
    }

    private String header(Map<String, String> headers, String name) {
        return headers.entrySet().stream()
            .filter(e -> e.getKey().equalsIgnoreCase(name))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(null);
    }
}
