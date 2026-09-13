package com.extremis.hub.tools.kd;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KdCalculatorRequest {

    @NotNull(message = "must not be null")
    @Min(value = 0, message = "must be >= 0")
    private Integer kills;

    @NotNull(message = "must not be null")
    @Min(value = 0, message = "must be >= 0")
    private Integer deaths;

    private boolean save = false;
}
