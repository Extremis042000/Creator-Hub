package com.extremis.hub.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * gyroscopeUsage x scopeLevel, ranges with per-row confidence — see
 * docs/07-phase2-system-design.md §1.4 for the schema-pivot rationale
 * (the original deviceCategory x playStyle grid had zero evidentiary
 * support). A row with active=false or confidence=INSUFFICIENT_DATA
 * still exists (nullable ranges) rather than being omitted, so the
 * service can return an honest "not enough data yet" response.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "bgmi_sensitivity_profile")
@IdClass(BgmiSensitivityProfileId.class)
public class BgmiSensitivityProfile extends Auditable {

    @Id
    @Enumerated(EnumType.STRING)
    private GyroscopeUsage gyroscopeUsage;

    @Id
    @Enumerated(EnumType.STRING)
    private ScopeLevel scopeLevel;

    private Integer recommendedMin;
    private Integer recommendedMax;
    private Integer recommendedStartingValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BgmiConfidenceLevel confidence;

    private String sourceReference;
    private Instant lastVerifiedAt;

    @Column(nullable = false)
    private boolean active = false;
}
