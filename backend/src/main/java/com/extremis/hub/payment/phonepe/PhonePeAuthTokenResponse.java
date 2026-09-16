package com.extremis.hub.payment.phonepe;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Response shape of POST {authBaseUrl}/v1/oauth/token -- see PhonePe's Authorization API reference. */
@JsonIgnoreProperties(ignoreUnknown = true)
record PhonePeAuthTokenResponse(
    @JsonProperty("access_token") String accessToken,
    @JsonProperty("expires_at") Long expiresAtEpochSeconds) {
}
