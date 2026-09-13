package com.extremis.hub.digitalproducts;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies short-lived, HMAC-signed download tokens -- the
 * "Spring Boot signed download URLs" named in the roadmap. A token is
 * "<base64url(productId:expiryEpochSeconds)>.<base64url(hmacSha256)>";
 * DownloadController trusts a token purely on signature + expiry, no
 * separate auth header needed (the token itself is the credential,
 * same model as a presigned S3 URL).
 */
@Service
@RequiredArgsConstructor
public class DownloadTokenService {

    private final DigitalProductProperties properties;
    private SecretKeySpec signingKey;

    @PostConstruct
    void init() {
        if (properties.getDownloadSigningSecret() == null || properties.getDownloadSigningSecret().length() < 32) {
            throw new IllegalStateException(
                "extremis.digital-products.download-signing-secret (DOWNLOAD_SIGNING_SECRET) must be set and at least 32 characters long.");
        }
        this.signingKey = new SecretKeySpec(
            properties.getDownloadSigningSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    public String issueToken(UUID productId) {
        long expiryEpoch = Instant.now()
            .plus(properties.getDownloadLinkExpiryMinutes(), ChronoUnit.MINUTES)
            .getEpochSecond();
        String payload = productId + ":" + expiryEpoch;
        return base64Url(payload) + "." + sign(payload);
    }

    /** Empty if the token is missing, malformed, expired, or has a bad signature. */
    public Optional<UUID> verifyAndGetProductId(String token) {
        try {
            int dot = token.indexOf('.');
            if (dot < 0) {
                return Optional.empty();
            }
            String payload = new String(
                Base64.getUrlDecoder().decode(token.substring(0, dot)), StandardCharsets.UTF_8);
            String providedSignature = token.substring(dot + 1);

            String expectedSignature = sign(payload);
            if (!MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                providedSignature.getBytes(StandardCharsets.UTF_8))) {
                return Optional.empty();
            }

            String[] parts = payload.split(":", 2);
            long expiryEpoch = Long.parseLong(parts[1]);
            if (Instant.now().getEpochSecond() > expiryEpoch) {
                return Optional.empty();
            }
            return Optional.of(UUID.fromString(parts[0]));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(signingKey);
            byte[] signature = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign download token", e);
        }
    }

    private String base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
