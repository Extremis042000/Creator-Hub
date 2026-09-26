package com.extremis.hub.ai;

/**
 * referenceImageDataUri is optional (null for a from-scratch generation) --
 * either a public image URL or a base64 data URI
 * ("data:image/png;base64,...."), both live-verified against the FreeModel
 * gateway (2026-09-26; see OpenAiCompatibleImageProvider's class Javadoc for
 * the real size ceiling a caller must respect on the base64 path).
 * maxImages is this path's cost-ceiling knob, the equivalent of
 * AiGenerationRequest's maxOutputTokens -- an image call has no token budget
 * to cap (usage.input_tokens/output_tokens/total_tokens are always reported
 * as 0 for image calls), so the real cost lever is how many images one call
 * asks for. Every caller so far uses the 3-arg constructor, which defaults
 * it to 1.
 */
public record ImageGenerationRequest(String prompt, String referenceImageDataUri, int width, int height, int maxImages) {

    public ImageGenerationRequest(String prompt, int width, int height) {
        this(prompt, null, width, height, 1);
    }
}
