package com.extremis.hub.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GrantPremiumRequest {

    @NotBlank(message = "must not be blank")
    @Email(message = "must be a valid email")
    private String email;
}
