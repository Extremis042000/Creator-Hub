package com.extremis.hub.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GoogleAuthRequest {

    /** The ID token from Google Identity Services on the frontend, not an access token. */
    @NotBlank(message = "must not be blank")
    private String idToken;
}
