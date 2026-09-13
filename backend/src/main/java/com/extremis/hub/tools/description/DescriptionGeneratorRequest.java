package com.extremis.hub.tools.description;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DescriptionGeneratorRequest {

    @NotBlank(message = "must not be blank")
    @Size(max = 100, message = "must be at most 100 characters")
    private String game;

    @NotBlank(message = "must not be blank")
    @Size(max = 100, message = "must be at most 100 characters")
    private String topic;

    @NotBlank(message = "must not be blank")
    @Size(max = 100, message = "must be at most 100 characters")
    private String channelName;

    @Size(max = 15, message = "must contain at most 15 keywords")
    private List<String> keywords = List.of();

    @Valid
    private List<SocialLinkRequest> socialLinks = List.of();

    private boolean save = false;
}
