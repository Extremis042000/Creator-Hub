package com.extremis.hub.auth;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Backed by extremis.auth.jwt.* (env vars JWT_SECRET, JWT_EXPIRATION_MINUTES). */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "extremis.auth.jwt")
public class JwtProperties {
    /** Must be at least 256 bits (32 chars) for HS256 -- see JwtService. */
    private String secret;
    private long expirationMinutes = 10080; // 7 days -- MVP simplicity, no refresh token yet
}
