package com.extremis.hub.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

/**
 * See docs/07-phase2-system-design.md §1.6. `active` is the only
 * lifecycle control -- no soft-delete field, matching Tool: admins
 * deactivate a stale/expired link rather than deleting the row (which
 * would orphan its AffiliateClick history).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
public class AffiliateProduct extends Auditable {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false)
    private String name;

    private String brand;

    private String category;

    private String priceInfo;

    @Column(nullable = false, length = 2048)
    private String affiliateUrl;

    private String merchant;

    private String region;

    @Column(length = 2048)
    private String imageUrl;

    @Column(length = 1000)
    private String disclosureText;

    @Column(nullable = false)
    private boolean active = true;
}
