package com.extremis.hub.ai;

/**
 * Provider-agnostic single-shot image generation (Phase 40) -- the image
 * counterpart to AiGenerationProvider. Injected as
 * Optional<ImageGenerationProvider>: empty until real credentials are
 * configured. Unlike AiGenerationProvider there is no template/deterministic
 * fallback for an image -- a caller that needs one must gate the whole
 * feature behind this being present (see
 * docs/decisions/10-ai-thumbnail-generator.md).
 */
public interface ImageGenerationProvider {

    /** e.g. "openai-compatible-image". */
    String getProviderName();

    /** The concrete model in use, for logging/cost visibility. */
    String getModelName();

    /** Throws ImageGenerationException on any failure or empty output -- callers must handle it, no fallback exists. */
    ImageGenerationResult generate(ImageGenerationRequest request);
}
