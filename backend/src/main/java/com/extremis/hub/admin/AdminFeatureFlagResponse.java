package com.extremis.hub.admin;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AdminFeatureFlagResponse {
    String key;
    boolean enabled;
    int rolloutPercent;
}
