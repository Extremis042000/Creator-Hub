package com.extremis.hub.admin;

import java.util.UUID;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AdminUserSummary {
    UUID userId; // null if an ADMIN_EMAILS entry has never signed in yet
    String email;
    String displayName;
    AdminSource source;

    public enum AdminSource {
        /** Founder allowlist (ADMIN_EMAILS) -- not revocable from this API. */
        ENV,
        /** Granted at runtime by another admin -- revocable. */
        GRANTED
    }
}
