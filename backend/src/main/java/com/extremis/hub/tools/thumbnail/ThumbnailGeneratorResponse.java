package com.extremis.hub.tools.thumbnail;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

@Value
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ThumbnailGeneratorResponse {
    String imageUrl;
    int width;
    int height;
}
