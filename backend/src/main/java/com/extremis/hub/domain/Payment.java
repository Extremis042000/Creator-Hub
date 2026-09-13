package com.extremis.hub.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

/** provider="test" + status="succeeded" rows are Phase 19's stand-in for a real PaymentProvider (Phase 20). */
@Getter
@Setter
@NoArgsConstructor
@Entity
public class Payment extends Auditable {

    @Id
    @UuidGenerator
    private UUID id;

    @OneToOne(optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @Column(nullable = false)
    private String provider;

    private String providerRef;

    @Column(nullable = false)
    private String status;

    private Instant verifiedAt;
}
