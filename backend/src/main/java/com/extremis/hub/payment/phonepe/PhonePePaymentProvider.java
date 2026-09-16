package com.extremis.hub.payment.phonepe;

import com.extremis.hub.domain.Order;
import com.extremis.hub.payment.CheckoutSessionResult;
import com.extremis.hub.payment.PaymentProvider;
import com.extremis.hub.payment.PaymentWebhookEvent;
import com.extremis.hub.web.BusinessRuleViolationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Verified against PhonePe's own published Standard Checkout v2 API
 * docs (OAuth authorization, Create Payment, webhook payload shape) as
 * of 2026-09 -- NOT yet exercised against a real PhonePe account: no
 * sandbox credentials exist yet (pending the founder's PhonePe
 * onboarding), and the webhook endpoint isn't reachable from PhonePe's
 * servers without a public tunnel (e.g. ngrok) during local testing.
 * Treat this as "correct per the documented contract, unverified live"
 * until both of those are in place -- same honesty caveat the prior
 * Cashfree implementation carried.
 *
 * One genuine simplification over Cashfree: PhonePe's Create Payment
 * request needs no customer contact details at all (no phone-number
 * placeholder hack required), and its response includes a real,
 * PhonePe-hosted redirectUrl the frontend can send the browser to
 * directly -- no client-side JS SDK step like Cashfree's paymentSessionId
 * flow needed.
 *
 * Known constraint: Standard Checkout v2's amount field and this whole
 * flow are INR-only (PhonePe is a UPI-first Indian payment gateway) --
 * see the currency guard in createCheckoutSession. Order.totalCents is
 * already in the smallest currency unit, which for INR is paise, so no
 * unit conversion is needed (unlike Cashfree, which wanted decimal
 * rupees).
 *
 * One documented ambiguity, called out rather than silently assumed:
 * PhonePe's own docs state the webhook Authorization header is
 * "SHA256(username:password)" without specifying hex vs base64
 * encoding. This implementation hex-encodes (lowercase), matching the
 * encoding PhonePe's own older X-VERIFY checksum scheme uses and the
 * near-universal convention for this pattern -- verify against a real
 * webhook delivery once sandbox credentials exist, and adjust here if
 * PhonePe's actual behavior differs.
 */
public class PhonePePaymentProvider implements PaymentProvider {

    private final PhonePeProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();

    private volatile String cachedAccessToken;
    private volatile Instant cachedTokenExpiresAt = Instant.EPOCH;
    private final Object tokenLock = new Object();

    public PhonePePaymentProvider(PhonePeProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getProviderName() {
        return "phonepe";
    }

    @Override
    public CheckoutSessionResult createCheckoutSession(Order order, String successUrl, String cancelUrl) {
        if (!"INR".equalsIgnoreCase(order.getCurrency())) {
            throw new BusinessRuleViolationException(
                "PhonePe only supports INR -- this order is in " + order.getCurrency() + ".");
        }

        Map<String, Object> merchantUrls = new LinkedHashMap<>();
        // PhonePe has one redirectUrl for both outcomes (not a separate
        // cancel_url) -- same "one return URL, check status yourself"
        // pattern Cashfree used; cancelUrl is unused here too.
        merchantUrls.put("redirectUrl", successUrl);

        Map<String, Object> paymentFlow = new LinkedHashMap<>();
        paymentFlow.put("type", "PG_CHECKOUT");
        paymentFlow.put("merchantUrls", merchantUrls);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("merchantOrderId", order.getId().toString());
        // Order.totalCents is already the smallest currency unit --
        // for INR that's paise, exactly what PhonePe expects here.
        body.put("amount", order.getTotalCents());
        body.put("paymentFlow", paymentFlow);

        PhonePeCreatePaymentResponse response = restClient.post()
            .uri(checkoutBaseUrl() + "/checkout/v2/pay")
            .header("Authorization", "O-Bearer " + accessToken())
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(PhonePeCreatePaymentResponse.class);

        if (response == null || response.redirectUrl() == null) {
            throw new BusinessRuleViolationException(
                "PhonePe did not return a checkout redirect URL -- checkout could not be started.");
        }

        return new CheckoutSessionResult(response.orderId(), response.redirectUrl());
    }

    @Override
    public PaymentWebhookEvent verifyAndParseWebhook(String rawPayload, Map<String, String> headers) {
        String authorization = header(headers, "Authorization");
        if (authorization == null) {
            throw new BusinessRuleViolationException("Missing PhonePe webhook Authorization header.");
        }

        String expected = computeWebhookAuthorization();
        if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                authorization.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessRuleViolationException("PhonePe webhook signature verification failed.");
        }

        PhonePeWebhookPayload payload;
        try {
            payload = objectMapper.readValue(rawPayload, PhonePeWebhookPayload.class);
        } catch (Exception e) {
            throw new BusinessRuleViolationException("Malformed PhonePe webhook payload.");
        }

        if (payload.payload() == null || payload.payload().merchantOrderId() == null) {
            throw new BusinessRuleViolationException("PhonePe webhook payload is missing merchantOrderId.");
        }

        boolean successful = "checkout.order.completed".equals(payload.event())
            && "COMPLETED".equalsIgnoreCase(payload.payload().state());

        UUID orderId;
        try {
            orderId = UUID.fromString(payload.payload().merchantOrderId());
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleViolationException("PhonePe webhook merchantOrderId is not a valid order id.");
        }

        return new PaymentWebhookEvent(orderId, successful, payload.payload().orderId());
    }

    /** Hex(SHA256("username:password")) -- see class javadoc's encoding caveat. */
    private String computeWebhookAuthorization() {
        String credentials = properties.getWebhookUsername() + ":" + properties.getWebhookPassword();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(credentials.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** Fetches and caches an OAuth token, refreshing shortly before it actually expires. */
    private String accessToken() {
        Instant now = Instant.now();
        if (cachedAccessToken != null && now.isBefore(cachedTokenExpiresAt.minusSeconds(60))) {
            return cachedAccessToken;
        }
        synchronized (tokenLock) {
            if (cachedAccessToken != null && Instant.now().isBefore(cachedTokenExpiresAt.minusSeconds(60))) {
                return cachedAccessToken;
            }
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("client_id", properties.getClientId());
            form.add("client_secret", properties.getClientSecret());
            form.add("client_version", properties.getClientVersion());
            form.add("grant_type", "client_credentials");

            PhonePeAuthTokenResponse response = restClient.post()
                .uri(authBaseUrl() + "/v1/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(PhonePeAuthTokenResponse.class);

            if (response == null || response.accessToken() == null) {
                throw new BusinessRuleViolationException(
                    "PhonePe did not return an access token -- checkout could not be started.");
            }

            cachedAccessToken = response.accessToken();
            cachedTokenExpiresAt = response.expiresAtEpochSeconds() != null
                ? Instant.ofEpochSecond(response.expiresAtEpochSeconds())
                : Instant.now().plusSeconds(300);
            return cachedAccessToken;
        }
    }

    private String authBaseUrl() {
        return properties.isSandbox()
            ? "https://api-preprod.phonepe.com/apis/pg-sandbox"
            : "https://api.phonepe.com/apis/identity-manager";
    }

    private String checkoutBaseUrl() {
        return properties.isSandbox()
            ? "https://api-preprod.phonepe.com/apis/pg-sandbox"
            : "https://api.phonepe.com/apis/pg";
    }

    private String header(Map<String, String> headers, String name) {
        return headers.entrySet().stream()
            .filter(e -> e.getKey().equalsIgnoreCase(name))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(null);
    }
}
