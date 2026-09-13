package com.extremis.hub.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * active=false rows must never be used for a live conversion, even if
 * yawConstant is populated — see docs/06-prd.md §5.2's same-game
 * precedence rule and inactive-profile rejection requirement.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "sensitivity_game_profile")
public class SensitivityGameProfile extends Auditable {

    @Id
    @Enumerated(EnumType.STRING)
    private SupportedGame game;

    @Column(nullable = false)
    private Double yawConstant;

    @Column(nullable = false)
    private String formulaVersion;

    @Column(nullable = false)
    private String sourceReference;

    private Instant lastVerifiedAt;

    @Column(nullable = false)
    private boolean active = false;
}
