package com.extremis.hub.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * A user_id present here has admin access, granted at runtime by
 * another admin -- distinct from the ADMIN_EMAILS bootstrap allowlist
 * (AdminAccessProperties), which stays env-var-controlled. See
 * db/migration/V6__admin_grants.sql.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
public class AdminGrant {

    @Id
    private UUID userId;

    private UUID grantedByUserId;

    @CreationTimestamp
    private Instant createdAt;
}
