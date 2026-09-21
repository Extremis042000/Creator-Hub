package com.extremis.hub.ai;

/**
 * Provider-agnostic single-shot text generation (Phase 26): one prompt
 * in, one text result out -- no tools, no session, no memory across
 * calls. Injected as Optional<AiGenerationProvider>: empty until an API
 * key is configured, so callers must always have a non-AI fallback.
 */
public interface AiGenerationProvider {

    /** e.g. "anthropic". */
    String getProviderName();

    /** The concrete model in use, for logging/cost visibility. */
    String getModelName();

    /** Throws AiGenerationException on any failure, refusal, or empty output -- callers fall back. */
    AiGenerationResult generate(AiGenerationRequest request);
}
