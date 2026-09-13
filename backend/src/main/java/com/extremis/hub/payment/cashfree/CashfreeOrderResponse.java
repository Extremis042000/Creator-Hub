package com.extremis.hub.payment.cashfree;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Response shape of POST {baseUrl}/orders -- see Cashfree's Create Order API reference. */
@JsonIgnoreProperties(ignoreUnknown = true)
record CashfreeOrderResponse(
    @JsonProperty("cf_order_id") String cfOrderId,
    @JsonProperty("payment_session_id") String paymentSessionId,
    @JsonProperty("order_status") String orderStatus) {
}
