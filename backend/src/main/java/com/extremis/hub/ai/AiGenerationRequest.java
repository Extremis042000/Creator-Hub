package com.extremis.hub.ai;

/** maxOutputTokens is the per-call cost ceiling -- this product's equivalent of a hard spend cap for one request. */
public record AiGenerationRequest(String systemPrompt, String userPrompt, int maxOutputTokens) {
}
