package com.extremis.hub.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

/**
 * toolType (enum identifier) and slug (URL-facing string) are
 * deliberately separate fields — see
 * docs/07-phase2-system-design.md §1.2.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
public class Tool extends Auditable {

    @Id
    @UuidGenerator
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private ToolType toolType;

    @Column(nullable = false, unique = true)
    private String slug;

    private String name;

    private String category;

    private boolean premiumOnly = false;
}
