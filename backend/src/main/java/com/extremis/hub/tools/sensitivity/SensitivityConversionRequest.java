package com.extremis.hub.tools.sensitivity;

import com.extremis.hub.domain.SupportedGame;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SensitivityConversionRequest {

    @NotNull(message = "must not be null")
    private SupportedGame sourceGame;

    @NotNull(message = "must not be null")
    private SupportedGame targetGame;

    @NotNull(message = "must not be null")
    @Min(value = 100, message = "must be >= 100")
    @Max(value = 25600, message = "must be <= 25600")
    private Integer dpi;

    @NotNull(message = "must not be null")
    @DecimalMin(value = "0.0", inclusive = false, message = "must be > 0")
    private Double sourceSensitivity;

    private boolean save = false;
}
