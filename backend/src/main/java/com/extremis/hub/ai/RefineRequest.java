package com.extremis.hub.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Phase 29: "make it punchier" follow-up on an AI-generated tool result. Shared shape for every refinable tool. */
@Getter
@Setter
public class RefineRequest {

    @NotBlank
    private String sessionId;

    @NotBlank
    @Size(max = 300)
    private String message;
}
