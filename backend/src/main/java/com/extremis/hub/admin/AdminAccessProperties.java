package com.extremis.hub.admin;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Backed by extremis.admin.emails (env var ADMIN_EMAILS,
 * comma-separated). Deliberately NOT the AdminUser/AdminRole entity
 * designed in docs/07-phase2-system-design.md §1.5 -- that table has
 * a real bootstrapping problem (Flyway migrations run before the
 * founder's first Google sign-in creates their app_user row, so a
 * seed migration can't reference them), and building a role table for
 * one founder-only admin is building ahead of need. This env var lets
 * the founder grant/revoke admin access with a redeploy, no code
 * change and no migration -- matching "founder can manage content
 * without code changes." Revisit with a real DB-backed role once a
 * second admin (e.g. a moderator) actually exists.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "extremis.admin")
public class AdminAccessProperties {
    private List<String> emails = List.of();

    public boolean isAdmin(String email) {
        return email != null && emails.stream().anyMatch(e -> e.equalsIgnoreCase(email));
    }
}
