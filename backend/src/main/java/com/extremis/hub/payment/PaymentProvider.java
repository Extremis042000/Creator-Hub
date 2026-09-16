package com.extremis.hub.payment;

import com.extremis.hub.domain.Order;
import java.util.Map;

/**
 * Provider-agnostic checkout abstraction (Phase 20). Founder chose
 * PhonePe (Standard Checkout v2) -- see payment/phonepe/. CheckoutService
 * injects this as Optional<PaymentProvider>: zero beans until every
 * required PhonePe credential is set (real checkout returns a clear
 * "not configured" error; the free test-mode purchase on /store, built
 * in Phase 19, remains fully usable in the meantime), exactly one bean
 * once PaymentProviderConfig's gate passes.
 */
public interface PaymentProvider {

    /** e.g. "razorpay" -- stored on Payment.provider once a real purchase completes. */
    String getProviderName();

    /**
     * Starts a provider-hosted checkout for the given order. orderId
     * must round-trip through the provider (as session metadata/notes)
     * so the webhook can be matched back to it.
     */
    CheckoutSessionResult createCheckoutSession(Order order, String successUrl, String cancelUrl);

    /**
     * Verifies the webhook's signature against rawPayload before
     * trusting anything in it -- never parse an unverified payload.
     * Takes the full header map (not a single named header) because
     * providers differ in how many headers their signature scheme
     * needs -- PhonePe needs just Authorization, but a future provider
     * might need more than one. Throws BusinessRuleViolationException
     * if verification fails.
     */
    PaymentWebhookEvent verifyAndParseWebhook(String rawPayload, Map<String, String> headers);
}
