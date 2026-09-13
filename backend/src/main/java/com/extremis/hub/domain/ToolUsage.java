package com.extremis.hub.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * Written only when a tool endpoint is called with save=true — see
 * docs/06-prd.md §4 ("save=false writes nothing at all").
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(indexes = @Index(name = "idx_tool_usage_user", columnList = "user_id"))
public class ToolUsage extends Auditable {

    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne
    private User user; // nullable — anonymous usage

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ToolType toolType;

    @JdbcTypeCode(SqlTypes.JSON)
    private String inputSummary;
}
