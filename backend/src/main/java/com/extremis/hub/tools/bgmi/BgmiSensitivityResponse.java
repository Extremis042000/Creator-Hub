package com.extremis.hub.tools.bgmi;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BgmiSensitivityResponse {
    List<ScopeRecommendation> recommendations;
    String disclaimer;
    String shareToken;
}
