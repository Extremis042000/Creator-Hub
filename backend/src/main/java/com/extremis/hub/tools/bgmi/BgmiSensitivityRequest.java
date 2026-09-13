package com.extremis.hub.tools.bgmi;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BgmiSensitivityRequest {

    @NotNull(message = "must not be null")
    private Boolean usesGyroscope;

    private boolean save = false;
}
