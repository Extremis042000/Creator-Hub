package com.extremis.hub.domain;

import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Hibernate is the source of truth for these timestamps (see
 * docs/07-phase2-system-design.md §"Timestamp ownership"); SQL-side
 * DEFAULT now() in the Flyway migrations is only a safety-net default
 * for rows inserted outside Hibernate, not a substitute for this.
 */
@Getter
@MappedSuperclass
public abstract class Auditable {

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
