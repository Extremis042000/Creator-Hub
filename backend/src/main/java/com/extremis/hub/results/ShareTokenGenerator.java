package com.extremis.hub.results;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * Cryptographically secure, URL-safe, non-sequential — never a
 * predictable/incrementing value. See docs/06-prd.md §4.2. 16 random
 * bytes -> 22-character Base64URL string (no padding) = 128 bits of
 * entropy, matching the PRD's stated minimum.
 */
@Component
public class ShareTokenGenerator {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    public String generate() {
        byte[] bytes = new byte[16];
        SECURE_RANDOM.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }
}
