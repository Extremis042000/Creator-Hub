package com.extremis.hub.results;

import com.extremis.hub.domain.ToolType;
import java.time.Instant;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

/**
 * GET /api/v1/results/{shareToken} response shape — docs/06-prd.md
 * §4.2. `result` is a plain Map, not a Jackson JsonNode — whichever
 * HTTP message converter Spring MVC actually uses at runtime may not
 * special-case JsonNode as a raw tree (confirmed: Boot 4's converter
 * doesn't), so a JsonNode field gets reflectively serialized as its
 * own accessor methods (isArray, isObject, ...) instead of its
 * content. A generic Map is universally recognized as a plain JSON
 * object by any Jackson-family converter.
 */
@Value
@Builder
public class SharedResultResponse {
    ToolType toolType;
    Map<String, Object> result;
    Instant createdAt;
    Instant expiresAt;
}
