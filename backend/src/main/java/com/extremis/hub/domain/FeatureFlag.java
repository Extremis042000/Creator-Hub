package com.extremis.hub.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
public class FeatureFlag extends Auditable {

    @Id
    private String key;

    @Column(nullable = false)
    private boolean enabled = false;

    @Column(nullable = false)
    private int rolloutPercent = 0;
}
