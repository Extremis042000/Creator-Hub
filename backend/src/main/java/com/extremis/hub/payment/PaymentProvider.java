package com.extremis.hub.payment;

import com.extremis.hub.domain.Order;
import java.util.Map;

/**
 * Provider-agnostic checkout abstraction (Phase 20). No concrete
 * implementation exists yet -- that requires the founder to choose a
 * real provider (e.g. Razorpay) and complete business KYC first (see
 * docs/05-founder-action-checklist.md). CheckoutService injects this
 * as Optional<PaymentProvider>: zero beans today (real checkout
 * returns a clear "not configured" error; the free test-mode purchase
 * on /store, built in Phase 19, remains fully usable), exactly one
 * once a founder-approved implementation (e.g. RazorpayPaymentProvider)
 * is added as a @Component.
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
     * needs -- Cashfree needs both x-webhook-signature AND
     * x-webhook-timestamp, for example. Throws
     * BusinessRuleViolationException if verification fails.
     */
    PaymentWebhookEvent verifyAndParseWebhook(String rawPayload, Map<String, String> headers);
}
