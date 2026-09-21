package com.extremis.hub.ai;

/** servedBy = the model that actually produced the text (gateways route, so it can differ from the configured one). */
public record AiGenerationResult(String text, long inputTokens, long outputTokens, String servedBy) {
}
