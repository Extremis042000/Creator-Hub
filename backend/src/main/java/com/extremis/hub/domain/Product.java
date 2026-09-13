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
 * fileRef is a filename inside the secure files directory (see
 * DigitalProductProperties) -- never a public path or URL. `active`
 * is the only lifecycle toggle admins get; deletedAt exists for a
 * future hard-delete workflow but nothing sets it yet (matches Tool's
 * no-delete lifecycle pattern -- deactivate, don't delete, so
 * existing Orders/OrderItems never dangle).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
public class Product extends Auditable {

    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private ProductCategory category;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private long priceCents;

    @Column(nullable = false)
    private String currency = "USD";

    @Column(nullable = false)
    private String fileRef;

    @Column(nullable = false)
    private boolean active = true;

    private Instant deletedAt;
}
