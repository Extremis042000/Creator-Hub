package com.extremis.hub.tools.description;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DescriptionGeneratorResponse {
    String description;
    String seoKeywordsSection;
    List<String> hashtags;
    String shareToken;
    /** Phase 29: present only when AI generated this result -- pass it to POST .../refine to iterate ("make it shorter"). Null for template output, which has no conversation to refine. */
    String refineSessionId;
}
