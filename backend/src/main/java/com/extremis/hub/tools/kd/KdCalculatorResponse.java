package com.extremis.hub.tools.kd;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

/**
 * Fields kept null (kdRatio/kills/deaths/shareToken) are omitted from
 * the JSON entirely rather than serialized as null, matching the PRD
 * examples exactly. kdRatio is null whenever undefined -- never a
 * numeric stand-in like `kills` -- see docs/06-prd.md §5.1.
 */
@Value
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KdCalculatorResponse {
    Double kdRatio;
    String displayValue;
    Integer kills;
    Integer deaths;
    PerformanceCategory performanceCategory;

    // Field named to avoid Lombok/Jackson's "is"-prefix stripping
    // ambiguity on boolean getters; @JsonProperty pins the wire name.
    @JsonProperty("isUndefined")
    boolean undefinedFlag;

    String shareToken;
}
