package com.extremis.hub.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * Shareable result, written only on save=true. Anonymous results use
 * a null user. expiresAt = createdAt + 30 days for anonymous results;
 * a scheduled job hard-deletes expired rows, but a lookup checks
 * expiresAt at read time regardless (see docs/06-prd.md §4.1/§4.2).
 * No separate index on shareToken: the UNIQUE constraint already
 * creates one in PostgreSQL.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "generated_result")
public class GeneratedResult extends Auditable {

    @Id
    @UuidGenerator
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ToolType toolType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String inputJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String outputJson;

    @Column(nullable = false, unique = true, length = 32)
    private String shareToken;

    @ManyToOne
    private User user; // nullable — anonymous result

    @Column(nullable = false)
    private Instant expiresAt;
}
