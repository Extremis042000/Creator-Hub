package com.extremis.hub.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminFeatureFlagUpsertRequest {

    private boolean enabled;

    @Min(value = 0, message = "must be >= 0")
    @Max(value = 100, message = "must be <= 100")
    private int rolloutPercent;
}
