package com.extremis.hub.tools.thumbnail;

import com.extremis.hub.ai.AiUsageGuard;
import com.extremis.hub.ai.ImageGenerationException;
import com.extremis.hub.ai.ImageGenerationProvider;
import com.extremis.hub.ai.ImageGenerationRequest;
import com.extremis.hub.ai.ImageGenerationResult;
import com.extremis.hub.domain.ToolType;
import com.extremis.hub.tools.common.TextSanitizer;
import com.extremis.hub.web.BusinessRuleViolationException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Phase 41. Unlike Title/Description, there is no deterministic
 * template fallback for an image (see
 * docs/decisions/10-ai-thumbnail-generator.md Sec4) -- this tool is
 * premium-only from day one (its seeded Tool row has
 * premiumOnly=true, enforced by PremiumAccessService in the
 * controller before this class is ever reached) and ANY problem (no
 * provider configured, rate-limited, a failed call) throws a clear
 * BusinessRuleViolationException instead of silently degrading -- the
 * same discipline as TitleGeneratorService#refine, which also has no
 * fallback to degrade to.
 */
@Service
@RequiredArgsConstructor
public class ThumbnailGeneratorService {

    // Real YouTube thumbnail resolution (16:9) -- live-verified good quality
    // at this exact size in Phase 40.
    private static final int THUMBNAIL_WIDTH = 1280;
    private static final int THUMBNAIL_HEIGHT = 720;

    private static final Map<Tone, String> TONE_DESCRIPTORS = Map.of(
        Tone.HYPE, "explosive, high-energy, intense lighting",
        Tone.CASUAL, "relaxed, warm, friendly atmosphere",
        Tone.COMPETITIVE, "sharp, focused, ranked-esports intensity",
        Tone.FUNNY, "playful, exaggerated, comedic expression"
    );

    private static final Map<VideoType, String> VIDEO_TYPE_DESCRIPTORS = Map.of(
        VideoType.HIGHLIGHT, "a dramatic highlight-reel action moment",
        VideoType.TUTORIAL, "a clean, instructional composition",
        VideoType.MONTAGE, "a fast-paced, multi-moment collage feel",
        VideoType.VLOG, "a personal, lifestyle vlog framing",
        VideoType.LIVESTREAM_RECAP, "a stream-recap highlight moment"
    );

    private final TextSanitizer sanitizer;
    private final Optional<ImageGenerationProvider> imageProvider;
    private final AiUsageGuard usageGuard;
    private final ReferenceImagePreparer referenceImagePreparer;

    public ThumbnailGeneratorResponse generate(ThumbnailGeneratorRequest request, UUID userId) {
        ImageGenerationProvider provider = imageProvider.orElseThrow(
            () -> new BusinessRuleViolationException("AI thumbnail generation isn't available right now."));

        String denialReason = usageGuard.tryAcquire(userId);
        if (denialReason != null) {
            usageGuard.recordThrottled(userId, ToolType.THUMBNAIL_GENERATOR, provider.getProviderName(), denialReason);
            throw new BusinessRuleViolationException("Too many AI requests right now -- please try again in a moment.");
        }

        // Downscale/re-encode BEFORE spending the acquired slot's call --
        // a rejected upload should never reach the provider, but the rate-
        // limit slot is already spent by this point; that's an acceptable
        // small cost (matches the fail-open direction: a rejected upload
        // is a client error, not one that should be retried anyway).
        String referenceImageDataUri = isBlank(request.getReferenceImageBase64())
            ? null : referenceImagePreparer.prepare(request.getReferenceImageBase64());

        String prompt = buildPrompt(request);

        long started = System.currentTimeMillis();
        try {
            ImageGenerationResult result = provider.generate(new ImageGenerationRequest(
                prompt, referenceImageDataUri, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT, 1));

            // input/output tokens are always 0 for image calls on this gateway
            // (Phase 40 live finding) -- no real per-call cost visibility exists
            // for images the way it does for text.
            usageGuard.recordSuccess(userId, ToolType.THUMBNAIL_GENERATOR, provider.getProviderName(),
                provider.getModelName(), result.servedBy(), 0, 0, System.currentTimeMillis() - started);

            return ThumbnailGeneratorResponse.builder()
                .imageUrl(result.imageUrl())
                .width(result.width())
                .height(result.height())
                .build();
        } catch (ImageGenerationException e) {
            usageGuard.recordFailure(userId, ToolType.THUMBNAIL_GENERATOR, provider.getProviderName(),
                e.getClass().getSimpleName(), System.currentTimeMillis() - started);
            throw new BusinessRuleViolationException("Couldn't generate a thumbnail right now -- please try again.");
        }
    }

    private String buildPrompt(ThumbnailGeneratorRequest request) {
        String game = sanitizer.sanitize(request.getGame());
        String topic = sanitizer.sanitize(request.getTopic());
        String toneDescriptor = TONE_DESCRIPTORS.get(request.getTone());
        String videoTypeDescriptor = VIDEO_TYPE_DESCRIPTORS.get(request.getVideoType());
        List<String> keywords = request.getKeywords() == null ? List.of() :
            request.getKeywords().stream().map(sanitizer::sanitize).toList();

        StringBuilder prompt = new StringBuilder()
            .append("A YouTube thumbnail for a gaming video about \"").append(game)
            .append("\": ").append(topic).append(". ")
            .append("Style: ").append(toneDescriptor).append(", ").append(videoTypeDescriptor).append(". ");
        if (!keywords.isEmpty()) {
            prompt.append("Visually reflects: ").append(String.join(", ", keywords)).append(". ");
        }
        prompt.append("High quality, visually striking, bold composition, no text overlay.");
        return prompt.toString();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
