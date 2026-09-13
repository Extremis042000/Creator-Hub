package com.extremis.hub.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("test-secret-at-least-32-characters-long-for-hs256");
        properties.setExpirationMinutes(60);
        jwtService = new JwtService(properties);
        jwtService.init();
    }

    @Test
    void issuedTokenValidatesBackToTheSameUserId() {
        UUID userId = UUID.randomUUID();
        String token = jwtService.issueToken(userId, "player@example.com");

        assertThat(jwtService.validateAndGetUserId(token)).contains(userId);
    }

    @Test
    void rejectsGarbageToken() {
        assertThat(jwtService.validateAndGetUserId("not-a-real-jwt")).isEmpty();
    }

    @Test
    void rejectsTokenSignedWithADifferentSecret() {
        JwtProperties otherProperties = new JwtProperties();
        otherProperties.setSecret("a-totally-different-secret-that-is-also-32-plus-chars");
        JwtService otherService = new JwtService(otherProperties);
        otherService.init();

        String tokenFromOtherService = otherService.issueToken(UUID.randomUUID(), "x@example.com");

        assertThat(jwtService.validateAndGetUserId(tokenFromOtherService)).isEmpty();
    }

    @Test
    void rejectsExpiredToken() {
        JwtProperties expiredProperties = new JwtProperties();
        expiredProperties.setSecret("test-secret-at-least-32-characters-long-for-hs256");
        expiredProperties.setExpirationMinutes(-1); // already expired the instant it's issued
        JwtService expiredService = new JwtService(expiredProperties);
        expiredService.init();

        String token = expiredService.issueToken(UUID.randomUUID(), "x@example.com");

        assertThat(jwtService.validateAndGetUserId(token)).isEmpty();
    }
}
