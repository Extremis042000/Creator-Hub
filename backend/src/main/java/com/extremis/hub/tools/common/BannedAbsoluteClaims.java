package com.extremis.hub.tools.common;

import java.util.List;
import java.util.Locale;

/**
 * Unsubstantiated absolute claims ("world record", "best ever", ...)
 * that must never appear in generated copy. The static template bank
 * (title/description generators) is human-reviewed once to never
 * contain these -- see each service's TemplateGeneratorServiceTest --
 * but AI-generated output (Phase 27) can't be pre-audited the same
 * way, so it's checked against this same list at runtime instead. One
 * shared list so the test and the runtime check can't drift apart.
 */
public final class BannedAbsoluteClaims {

    public static final List<String> PHRASES = List.of(
        "WORLD RECORD", "#1 IN THE WORLD", "BEST EVER", "GREATEST OF ALL TIME");

    private BannedAbsoluteClaims() {
    }

    public static boolean containsBannedClaim(String text) {
        if (text == null) return false;
        String upper = text.toUpperCase(Locale.ROOT);
        return PHRASES.stream().anyMatch(upper::contains);
    }
}
