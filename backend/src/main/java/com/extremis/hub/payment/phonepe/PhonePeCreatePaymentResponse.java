package com.extremis.hub.payment.phonepe;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Response shape of POST {checkoutBaseUrl}/checkout/v2/pay -- see PhonePe's Create Payment API reference. */
@JsonIgnoreProperties(ignoreUnknown = true)
record PhonePeCreatePaymentResponse(String orderId, String state, String redirectUrl) {
}
