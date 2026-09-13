package com.extremis.hub.admin;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AdminPremiumUserResponse {
    UUID userId;
    String email;
    String displayName;
    Instant grantedAt;
}
