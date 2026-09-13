package com.extremis.hub.payment.cashfree;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.extremis.hub.payment.PaymentWebhookEvent;
import com.extremis.hub.web.BusinessRuleViolationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Only the webhook signature verification is unit-tested here --
 * createCheckoutSession makes a real HTTP call to Cashfree and can't
 * be exercised without live sandbox credentials (see
 * CashfreePaymentProvider's class javadoc).
 */
class CashfreePaymentProviderTest {

    private static final String SECRET = "test-cashfree-client-secret-32-chars-min";

    private CashfreePaymentProvider provider;

    @BeforeEach
    void setUp() {
        CashfreeProperties properties = new CashfreeProperties();
        properties.setClientId("test-client-id");
        properties.setClientSecret(SECRET);
        provider = new CashfreePaymentProvider(properties, new ObjectMapper());
    }

    private String independentlyComputedSignature(String timestamp, String rawBody) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signature = mac.doFinal((timestamp + rawBody).getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signature);
    }

    @Test
    void acceptsAValidSignatureAndParsesASuccessfulPayment() throws Exception {
        UUID orderId = UUID.randomUUID();
        String payload = """
            {
              "data": {
                "order": { "order_id": "%s" },
                "payment": { "cf_payment_id": "5114933189368", "payment_status": "SUCCESS" }
              },
              "event_time": "2026-07-30T14:14:27+05:30",
              "type": "PAYMENT_SUCCESS_WEBHOOK"
            }
            """.formatted(orderId);
        String timestamp = "1753861467";
        String signature = independentlyComputedSignature(timestamp, payload);

        PaymentWebhookEvent event = provider.verifyAndParseWebhook(
            payload, Map.of("x-webhook-signature", signature, "x-webhook-timestamp", timestamp));

        assertThat(event.orderId()).isEqualTo(orderId);
        assertThat(event.successful()).isTrue();
        assertThat(event.providerPaymentRef()).isEqualTo("5114933189368");
    }

    @Test
    void treatsANonSuccessPaymentStatusAsUnsuccessful() throws Exception {
        UUID orderId = UUID.randomUUID();
        String payload = """
            {
              "data": {
                "order": { "order_id": "%s" },
                "payment": { "cf_payment_id": "abc", "payment_status": "FAILED" }
              },
              "type": "PAYMENT_FAILED_WEBHOOK"
            }
            """.formatted(orderId);
        String timestamp = "1753861467";
        String signature = independentlyComputedSignature(timestamp, payload);

        PaymentWebhookEvent event = provider.verifyAndParseWebhook(
            payload, Map.of("x-webhook-signature", signature, "x-webhook-timestamp", timestamp));

        assertThat(event.successful()).isFalse();
    }

    @Test
    void rejectsATamperedSignature() {
        String payload = "{\"data\":{\"order\":{\"order_id\":\"" + UUID.randomUUID() + "\"}}}";
        Map<String, String> headers = Map.of(
            "x-webhook-signature", "not-the-real-signature",
            "x-webhook-timestamp", "1753861467");

        assertThatThrownBy(() -> provider.verifyAndParseWebhook(payload, headers))
            .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void rejectsAPayloadSignedWithADifferentSecret() throws Exception {
        String payload = "{\"data\":{\"order\":{\"order_id\":\"" + UUID.randomUUID() + "\"}}}";
        String timestamp = "1753861467";

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("a-totally-different-secret-32-chars".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String wrongSignature = Base64.getEncoder().encodeToString(mac.doFinal((timestamp + payload).getBytes(StandardCharsets.UTF_8)));

        Map<String, String> headers = Map.of(
            "x-webhook-signature", wrongSignature,
            "x-webhook-timestamp", timestamp);

        assertThatThrownBy(() -> provider.verifyAndParseWebhook(payload, headers))
            .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void rejectsMissingSignatureHeaders() {
        String payload = "{\"data\":{\"order\":{\"order_id\":\"" + UUID.randomUUID() + "\"}}}";

        assertThatThrownBy(() -> provider.verifyAndParseWebhook(payload, Map.of()))
            .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void headerLookupIsCaseInsensitive() throws Exception {
        UUID orderId = UUID.randomUUID();
        String payload = "{\"data\":{\"order\":{\"order_id\":\"" + orderId + "\"}},\"type\":\"PAYMENT_SUCCESS_WEBHOOK\"}";
        String timestamp = "1753861467";
        String signature = independentlyComputedSignature(timestamp, payload);

        // Deliberately different casing than the provider looks up with.
        PaymentWebhookEvent event = provider.verifyAndParseWebhook(
            payload, Map.of("X-Webhook-Signature", signature, "X-Webhook-Timestamp", timestamp));

        assertThat(event.orderId()).isEqualTo(orderId);
    }
}
