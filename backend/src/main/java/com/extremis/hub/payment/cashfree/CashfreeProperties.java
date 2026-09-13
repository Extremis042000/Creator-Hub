package com.extremis.hub.payment.cashfree;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Backed by extremis.payment.cashfree.* (env vars CASHFREE_CLIENT_ID, CASHFREE_CLIENT_SECRET, etc.). */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "extremis.payment.cashfree")
public class CashfreeProperties {

    /** Left blank until the founder completes KYC and gets real credentials -- see PaymentProviderConfig. */
    private String clientId;
    private String clientSecret;

    /** true = https://sandbox.cashfree.com/pg, false = https://api.cashfree.com/pg -- flip only when ready to accept real money. */
    private boolean sandbox = true;

    /**
     * Pinned to a specific dated version (Cashfree's API versioning
     * scheme) rather than defaulting to "whatever is latest" -- avoids
     * silent breaking changes to the request/response shape. Verify
     * this is still a supported version in the Cashfree dashboard
     * before going live; override via CASHFREE_API_VERSION if not.
     */
    private String apiVersion = "2023-08-01";

    /** Our own backend's publicly-reachable URL for Cashfree to POST webhooks to -- see docs/05-founder-action-checklist.md. */
    private String notifyUrl = "http://localhost:8080/api/v1/webhooks/payment";
}
