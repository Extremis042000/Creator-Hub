package com.extremis.hub.tools.title;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TitleGeneratorResponse {
    List<String> titles;
    List<String> shortFormTitles;
    String shareToken;
}
