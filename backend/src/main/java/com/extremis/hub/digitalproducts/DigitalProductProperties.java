package com.extremis.hub.digitalproducts;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Backed by extremis.digital-products.* (env vars SECURE_FILES_DIR, DOWNLOAD_SIGNING_SECRET, TEST_PURCHASES_ENABLED). */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "extremis.digital-products")
public class DigitalProductProperties {
    /** Resolved relative to the backend process's working directory unless made absolute. Never web-exposed. */
    private String secureFilesDir = "secure-files";

    /** Must be at least 256 bits (32 chars) for HmacSHA256 -- see DownloadTokenService. */
    private String downloadSigningSecret;

    private long downloadLinkExpiryMinutes = 5;

    /**
     * Kill switch for the test-mode purchase flow (open to any signed-in
     * user per founder direction, since no real PaymentProvider exists
     * yet). Flip to false once Phase 20 lands a real provider, so this
     * never coexists with real payments.
     */
    private boolean testPurchasesEnabled = true;
}
