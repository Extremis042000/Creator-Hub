package com.extremis.hub.payment;

/** redirectUrl is the provider-hosted checkout page the frontend sends the browser to. */
public record CheckoutSessionResult(String providerSessionId, String redirectUrl) {
}
