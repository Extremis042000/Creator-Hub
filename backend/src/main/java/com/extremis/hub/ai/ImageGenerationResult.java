package com.extremis.hub.ai;

/**
 * width/height are what the gateway's own usage block reports it actually
 * served (falling back to the request's size if that block is missing) --
 * live-verified (2026-09-26) to match the requested size at both 512x512
 * and 1280x720. servedBy is the CONFIGURED model alias, not a live-detected
 * upstream like AiGenerationResult.servedBy() -- the image response has no
 * equivalent "model" field to read back (verified 2026-09-26: the real
 * response shape is {created, data, usage}, no model key).
 */
public record ImageGenerationResult(String imageUrl, int width, int height, String servedBy) {
}
