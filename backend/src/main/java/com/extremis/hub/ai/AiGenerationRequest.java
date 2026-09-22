package com.extremis.hub.ai;

import java.util.List;

/**
 * maxOutputTokens is the per-call cost ceiling -- this product's
 * equivalent of a hard spend cap for one request. history carries
 * prior turns for Phase 29's refine chat; every Phase 26/27 single-shot
 * caller uses the 3-arg constructor, which defaults it to empty --
 * providers that ignore history keep behaving exactly as before.
 */
public record AiGenerationRequest(String systemPrompt, List<ConversationTurn> history, String userPrompt, int maxOutputTokens) {

    public AiGenerationRequest(String systemPrompt, String userPrompt, int maxOutputTokens) {
        this(systemPrompt, List.of(), userPrompt, maxOutputTokens);
    }
}
