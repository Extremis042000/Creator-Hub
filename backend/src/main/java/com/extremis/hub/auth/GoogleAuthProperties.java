package com.extremis.hub.auth;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Backed by extremis.auth.google.client-id (env var GOOGLE_CLIENT_ID).
 * The founder creates this OAuth client themselves in Google Cloud
 * Console — see Founder Action Checklist. Only a Client ID is needed
 * (no secret): the frontend uses Google Identity Services to obtain
 * an ID token directly, and the backend verifies it against this
 * same Client ID as the expected audience.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "extremis.auth.google")
public class GoogleAuthProperties {
    private String clientId;
}
