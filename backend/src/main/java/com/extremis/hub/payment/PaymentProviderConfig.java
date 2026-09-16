package com.extremis.hub.payment;

import com.extremis.hub.payment.phonepe.PhonePePaymentProvider;
import com.extremis.hub.payment.phonepe.PhonePeProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers PhonePePaymentProvider only once real credentials are
 * present -- deliberately NOT @ConditionalOnProperty, which treats an
 * env var set to an empty string as "present" and would wrongly
 * activate this with blank credentials. Returning null here is the
 * standard Spring idiom for "no bean" (a NullBean is registered), so
 * Optional<PaymentProvider> elsewhere correctly resolves to empty
 * until every required PhonePe credential is set. Webhook username/
 * password are included in the gate too -- a provider that can create
 * checkout sessions but can never verify a webhook would silently
 * strand every real payment in PENDING forever.
 */
@Configuration
@RequiredArgsConstructor
public class PaymentProviderConfig {

    private final PhonePeProperties phonePeProperties;
    private final ObjectMapper objectMapper;

    @Bean
    public PaymentProvider phonePePaymentProvider() {
        if (isBlank(phonePeProperties.getClientId())
                || isBlank(phonePeProperties.getClientSecret())
                || isBlank(phonePeProperties.getClientVersion())
                || isBlank(phonePeProperties.getWebhookUsername())
                || isBlank(phonePeProperties.getWebhookPassword())) {
            return null;
        }
        return new PhonePePaymentProvider(phonePeProperties, objectMapper);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
