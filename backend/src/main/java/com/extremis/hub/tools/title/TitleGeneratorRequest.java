package com.extremis.hub.tools.title;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TitleGeneratorRequest {

    @NotBlank(message = "must not be blank")
    @Size(max = 100, message = "must be at most 100 characters")
    private String game;

    @NotBlank(message = "must not be blank")
    @Size(max = 100, message = "must be at most 100 characters")
    private String topic;

    @NotNull(message = "must not be null")
    private VideoType videoType;

    @NotNull(message = "must not be null")
    private Tone tone;

    @Valid
    @Size(max = 10, message = "must contain at most 10 keywords")
    private List<@Size(max = 40, message = "each keyword must be at most 40 characters") String> keywords = List.of();

    private boolean save = false;
}
