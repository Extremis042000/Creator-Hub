package com.extremis.hub.payment.cashfree;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Shape of a Cashfree PAYMENT_SUCCESS_WEBHOOK / PAYMENT_FAILED_WEBHOOK payload. */
@JsonIgnoreProperties(ignoreUnknown = true)
record CashfreeWebhookPayload(String type, Data data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Data(Order order, Payment payment) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Order(@JsonProperty("order_id") String orderId) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Payment(
            @JsonProperty("cf_payment_id") String cfPaymentId,
            @JsonProperty("payment_status") String paymentStatus) {
        }
    }
}
