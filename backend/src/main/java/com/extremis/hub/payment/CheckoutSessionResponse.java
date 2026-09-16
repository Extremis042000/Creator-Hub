package com.extremis.hub.payment;

/**
 * providerSessionId and redirectUrl are both nullable -- which one a
 * frontend uses depends on the provider. PhonePe always populates
 * redirectUrl (a plain hosted checkout page, browser navigates there
 * directly); a future provider driven client-side by its own JS SDK
 * would populate providerSessionId instead.
 */
public record CheckoutSessionResponse(String provider, String providerSessionId, String redirectUrl) {
}
