package com.extremis.hub.domain;

/**
 * BGMI's real settings screen has 7 zoom tiers; this schema
 * deliberately covers 5. NO_SCOPE/RED_DOT are unambiguous. SCOPE_3X
 * maps to the in-game "3x Scope" specifically (its "2x Scope" is not
 * represented). SNIPER_SCOPE maps to the in-game "8x Scope"
 * specifically (its "6x Scope" is not represented). See
 * docs/08-data-verification-report.md §8.2 for why.
 */
public enum ScopeLevel {
    NO_SCOPE,
    RED_DOT,
    SCOPE_3X,
    SCOPE_4X_ACOG,
    SNIPER_SCOPE
}
