package com.extremis.hub.payment.phonepe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.extremis.hub.payment.PaymentWebhookEvent;
import com.extremis.hub.web.BusinessRuleViolationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Only webhook signature verification is unit-tested here --
 * createCheckoutSession makes real HTTP calls to PhonePe (an OAuth
 * token fetch, then the create-payment call) and can't be exercised
 * without live sandbox credentials (see PhonePePaymentProvider's class
 * javadoc).
 */
class PhonePePaymentProviderTest {

    private static final String USERNAME = "test-webhook-user";
    private static final String PASSWORD = "test-webhook-password";

    private PhonePePaymentProvider provider;

    @BeforeEach
    void setUp() {
        PhonePeProperties properties = new PhonePeProperties();
        properties.setClientId("test-client-id");
        properties.setClientSecret("test-client-secret");
        properties.setClientVersion("1");
        properties.setWebhookUsername(USERNAME);
        properties.setWebhookPassword(PASSWORD);
        provider = new PhonePePaymentProvider(properties, new ObjectMapper());
    }

    private String independentlyComputedAuthorization(String username, String password) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    @Test
    void acceptsAValidSignatureAndParsesACompletedOrder() throws Exception {
        UUID orderId = UUID.randomUUID();
        String payload = """
            {
              "event": "checkout.order.completed",
              "payload": {
                "orderId": "OMO2403282020198641071317",
                "merchantId": "merchantId",
                "merchantOrderId": "%s",
                "state": "COMPLETED",
                "amount": 10000
              }
            }
            """.formatted(orderId);
        String authorization = independentlyComputedAuthorization(USERNAME, PASSWORD);

        PaymentWebhookEvent event = provider.verifyAndParseWebhook(
            payload, Map.of("Authorization", authorization));

        assertThat(event.orderId()).isEqualTo(orderId);
        assertThat(event.successful()).isTrue();
        assertThat(event.providerPaymentRef()).isEqualTo("OMO2403282020198641071317");
    }

    @Test
    void treatsAFailedOrderAsUnsuccessful() throws Exception {
        UUID orderId = UUID.randomUUID();
        String payload = """
            {
              "event": "checkout.order.failed",
              "payload": {
                "orderId": "OMO2403282020198641071311",
                "merchantOrderId": "%s",
                "state": "FAILED"
              }
            }
            """.formatted(orderId);
        String authorization = independentlyComputedAuthorization(USERNAME, PASSWORD);

        PaymentWebhookEvent event = provider.verifyAndParseWebhook(
            payload, Map.of("Authorization", authorization));

        assertThat(event.successful()).isFalse();
    }

    @Test
    void rejectsATamperedAuthorizationHeader() {
        String payload = "{\"payload\":{\"merchantOrderId\":\"" + UUID.randomUUID() + "\"}}";

        assertThatThrownBy(() -> provider.verifyAndParseWebhook(
                payload, Map.of("Authorization", "not-the-real-hash")))
            .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void rejectsAuthorizationComputedWithWrongCredentials() throws Exception {
        String payload = "{\"payload\":{\"merchantOrderId\":\"" + UUID.randomUUID() + "\"}}";
        String wrongAuthorization = independentlyComputedAuthorization("wrong-user", "wrong-password");

        assertThatThrownBy(() -> provider.verifyAndParseWebhook(
                payload, Map.of("Authorization", wrongAuthorization)))
            .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void rejectsMissingAuthorizationHeader() {
        String payload = "{\"payload\":{\"merchantOrderId\":\"" + UUID.randomUUID() + "\"}}";

        assertThatThrownBy(() -> provider.verifyAndParseWebhook(payload, Map.of()))
            .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void headerLookupIsCaseInsensitive() throws Exception {
        UUID orderId = UUID.randomUUID();
        String payload = "{\"event\":\"checkout.order.completed\",\"payload\":{\"orderId\":\"OMO1\",\"merchantOrderId\":\""
            + orderId + "\",\"state\":\"COMPLETED\"}}";
        String authorization = independentlyComputedAuthorization(USERNAME, PASSWORD);

        // Deliberately different casing than the provider looks up with.
        PaymentWebhookEvent event = provider.verifyAndParseWebhook(
            payload, Map.of("authorization", authorization));

        assertThat(event.orderId()).isEqualTo(orderId);
    }

    @Test
    void rejectsAMalformedMerchantOrderId() throws Exception {
        String payload = "{\"event\":\"checkout.order.completed\",\"payload\":{\"orderId\":\"OMO1\",\"merchantOrderId\":\"not-a-uuid\",\"state\":\"COMPLETED\"}}";
        String authorization = independentlyComputedAuthorization(USERNAME, PASSWORD);

        assertThatThrownBy(() -> provider.verifyAndParseWebhook(
                payload, Map.of("Authorization", authorization)))
            .isInstanceOf(BusinessRuleViolationException.class);
    }
}
