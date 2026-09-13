package com.extremis.hub.payment;

import java.util.UUID;

/** Parsed only after signature verification succeeds -- see PaymentProvider#verifyAndParseWebhook. */
public record PaymentWebhookEvent(UUID orderId, boolean successful, String providerPaymentRef) {
}
