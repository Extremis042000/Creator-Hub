package com.extremis.hub.payment;

/**
 * providerSessionId and redirectUrl are both nullable -- which one a
 * frontend uses depends on the provider (Cashfree's checkout is
 * driven client-side by its JS SDK using providerSessionId; a
 * provider with a plain hosted checkout page would populate
 * redirectUrl instead).
 */
public record CheckoutSessionResponse(String provider, String providerSessionId, String redirectUrl) {
}
