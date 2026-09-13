package com.extremis.hub.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

/** One row per redirect-endpoint hit. user is nullable -- most clicks will be anonymous. */
@Getter
@Setter
@NoArgsConstructor
@Entity
public class AffiliateClick extends Auditable {

    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "affiliate_product_id", nullable = false)
    private AffiliateProduct affiliateProduct;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "session_ref")
    private String sessionRef;
}
