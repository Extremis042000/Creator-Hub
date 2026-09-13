package com.extremis.hub.domain;

import java.io.Serializable;
import java.util.Objects;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
public class BgmiSensitivityProfileId implements Serializable {

    private GyroscopeUsage gyroscopeUsage;
    private ScopeLevel scopeLevel;

    public BgmiSensitivityProfileId(GyroscopeUsage gyroscopeUsage, ScopeLevel scopeLevel) {
        this.gyroscopeUsage = Objects.requireNonNull(gyroscopeUsage);
        this.scopeLevel = Objects.requireNonNull(scopeLevel);
    }
}
