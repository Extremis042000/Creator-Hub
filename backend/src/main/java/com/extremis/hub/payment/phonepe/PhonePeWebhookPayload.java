package com.extremis.hub.payment.phonepe;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Shape of a PhonePe Standard Checkout webhook -- covers both
 * checkout.order.completed and checkout.order.failed, which share one
 * payload structure (see PhonePe's Webhook Handling API reference).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record PhonePeWebhookPayload(String event, Payload payload) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Payload(String orderId, String merchantOrderId, String state) {
    }
}
