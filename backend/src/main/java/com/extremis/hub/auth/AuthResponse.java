package com.extremis.hub.auth;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AuthResponse {
    String token;
    CurrentUserResponse user;
}
