package com.extremis.hub.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

/**
 * entitlementKey is a product's UUID (as a string) for
 * source="product-purchase" rows -- Phase 22 (premium entitlements)
 * will introduce other entitlementKey values (e.g. "premium-tools")
 * for subscription-derived entitlements, sharing this same table.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
public class UserEntitlement extends Auditable {

    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String entitlementKey;

    @Column(nullable = false)
    private String source;

    @Column(nullable = false)
    private Instant grantedAt;

    private Instant expiresAt;
}
