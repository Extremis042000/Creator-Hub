package com.extremis.hub.ai;

public record AiGenerationResult(String text, long inputTokens, long outputTokens) {
}
