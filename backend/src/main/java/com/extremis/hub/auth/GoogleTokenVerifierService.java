package com.extremis.hub.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import jakarta.annotation.PostConstruct;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Verifies a Google ID token's signature, issuer, audience, and
 * expiry using Google's own library -- see docs/07-phase2-system-design.md
 * §4. Never trusts a client-supplied claim without this check.
 */
@Service
@RequiredArgsConstructor
public class GoogleTokenVerifierService {

    private final GoogleAuthProperties googleAuthProperties;
    private GoogleIdTokenVerifier verifier;

    @PostConstruct
    void init() {
        if (googleAuthProperties.getClientId() == null || googleAuthProperties.getClientId().isBlank()) {
            // Deliberately not fatal at startup: the rest of the app
            // (all 5 anonymous tools) must keep working even before
            // the founder has set up Google OAuth. Verification
            // simply always fails until GOOGLE_CLIENT_ID is set --
            // see AuthController's handling of an empty verifier result.
            return;
        }
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
            .setAudience(Collections.singletonList(googleAuthProperties.getClientId()))
            .build();
    }

    public record VerifiedGoogleUser(String subject, String email, String name) {}

    public Optional<VerifiedGoogleUser> verify(String idTokenString) {
        if (verifier == null) {
            return Optional.empty();
        }
        try {
            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                return Optional.empty();
            }
            GoogleIdToken.Payload payload = idToken.getPayload();
            String name = (String) payload.get("name");
            return Optional.of(new VerifiedGoogleUser(payload.getSubject(), payload.getEmail(), name));
        } catch (GeneralSecurityException | java.io.IOException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
