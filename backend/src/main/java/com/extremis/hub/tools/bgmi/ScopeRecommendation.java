package com.extremis.hub.tools.bgmi;

import com.extremis.hub.domain.BgmiConfidenceLevel;
import com.extremis.hub.domain.ScopeLevel;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

/**
 * recommendedMin/Max/StartingValue are null exactly when confidence
 * is INSUFFICIENT_DATA (or the backing row is inactive) — never a
 * fabricated number. See docs/06-prd.md §5.3.
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScopeRecommendation {
    ScopeLevel scopeLevel;
    String settingName;
    Integer recommendedMin;
    Integer recommendedMax;
    Integer recommendedStartingValue;
    BgmiConfidenceLevel confidence;
}
