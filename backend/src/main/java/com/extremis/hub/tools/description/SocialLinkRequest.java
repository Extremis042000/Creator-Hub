package com.extremis.hub.tools.description;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SocialLinkRequest {

    @NotBlank(message = "must not be blank")
    private String platform;

    @NotBlank(message = "must not be blank")
    @ValidHttpsUrl
    private String url;
}
