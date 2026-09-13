package com.extremis.hub.tools.sensitivity;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

@Value
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SensitivityConversionResponse {
    Double targetSensitivity;
    Double effectiveDpi;
    Double sourceCmPer360;
    Double targetCmPer360;
    String formulaVersion;
    String conversionNote;
    String shareToken;
}
