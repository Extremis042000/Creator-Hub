package com.extremis.hub.ai;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Phase 37: "the user copied this AI-generated result." Deliberately
 * carries no signal-type field -- REFINED and REGENERATED are only
 * ever inferred server-side (see GenerationSignalService); a client
 * asserting its own signal type would let a broken or malicious
 * frontend fabricate usage data. One endpoint per explicit signal,
 * same shape as RefineRequest.
 */
@Getter
@Setter
public class CopySignalRequest {

    @NotBlank
    private String sessionId;
}
