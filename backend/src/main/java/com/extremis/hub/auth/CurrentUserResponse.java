package com.extremis.hub.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CurrentUserResponse {
    UUID id;
    String email;
    String displayName;

    // Field named to avoid Lombok/Jackson's "is"-prefix stripping
    // ambiguity on boolean getters (same issue hit in
    // KdCalculatorResponse); @JsonProperty pins the wire name.
    @JsonProperty("isAdmin")
    boolean adminFlag;
}
