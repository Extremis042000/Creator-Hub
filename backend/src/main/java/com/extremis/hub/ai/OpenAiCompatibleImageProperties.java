package com.extremis.hub.ai;

import lombok.Getter;
import lombok.Setter;

/**
 * The image-generation counterpart to OpenAiCompatibleProperties (Phase 26)
 * -- a DIFFERENT endpoint shape from the chat-completions text path, not a
 * reuse of it. Live-verified (2026-09-26) against FreeModel's real
 * /v1/images/generations: request {model, prompt, n, size}, where size is
 * "WIDTH*HEIGHT" (not "WIDTHxHEIGHT"); response {created, data: [{url}],
 * usage: {width, height, image_count, input_tokens, output_tokens,
 * total_tokens}} -- the token fields are always 0 for image calls, no real
 * per-call cost visibility. No default for generationsUrl/model, same
 * discipline as OpenAiCompatibleProperties -- an unvetted host must be
 * named explicitly. apiKey has no default here either; the real Render
 * config reuses the existing AI_API_KEY value via a nested placeholder in
 * application.yml (${AI_IMAGE_API_KEY:${AI_API_KEY:}}) since it's the same
 * FreeModel account/gateway, not a second secret.
 */
@Getter
@Setter
public class OpenAiCompatibleImageProperties {

    /** Kill switch -- false leaves image generation off even with a key set. */
    private boolean enabled = true;

    /** The exact image-generation endpoint URL, e.g. https://host/v1/images/generations. */
    private String generationsUrl;
    private String apiKey;
    private String model;

    /** Image calls ran ~3.7-4.7s live (2026-09-26); a longer floor than the text path's default. */
    private int timeoutSeconds = 60;
}
