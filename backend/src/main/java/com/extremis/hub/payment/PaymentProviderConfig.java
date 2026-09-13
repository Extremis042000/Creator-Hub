package com.extremis.hub.payment;

import com.extremis.hub.payment.cashfree.CashfreePaymentProvider;
import com.extremis.hub.payment.cashfree.CashfreeProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers CashfreePaymentProvider only once real credentials are
 * present -- deliberately NOT @ConditionalOnProperty, which treats an
 * env var set to an empty string as "present" and would wrongly
 * activate this with blank credentials. Returning null here is the
 * standard Spring idiom for "no bean" (a NullBean is registered), so
 * Optional<PaymentProvider> elsewhere correctly resolves to empty
 * until CASHFREE_CLIENT_ID and CASHFREE_CLIENT_SECRET are both set.
 */
@Configuration
@RequiredArgsConstructor
public class PaymentProviderConfig {

    private final CashfreeProperties cashfreeProperties;
    private final ObjectMapper objectMapper;

    @Bean
    public PaymentProvider cashfreePaymentProvider() {
        if (isBlank(cashfreeProperties.getClientId()) || isBlank(cashfreeProperties.getClientSecret())) {
            return null;
        }
        return new CashfreePaymentProvider(cashfreeProperties, objectMapper);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
