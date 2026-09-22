package com.extremis.hub.tools.description;

import com.extremis.hub.ai.AiGenerationException;
import com.extremis.hub.ai.AiGenerationProvider;
import com.extremis.hub.ai.AiGenerationRequest;
import com.extremis.hub.ai.AiGenerationResult;
import com.extremis.hub.ai.AiUsageGuard;
import com.extremis.hub.ai.ConversationTurn;
import com.extremis.hub.ai.RefineSession;
import com.extremis.hub.ai.RefineSessionStore;
import com.extremis.hub.domain.ToolType;
import com.extremis.hub.tools.common.BannedAbsoluteClaims;
import com.extremis.hub.tools.common.TextSanitizer;
import com.extremis.hub.web.BusinessRuleViolationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Template composition remains the free-tier baseline -- zero AI
 * inference cost. See docs/06-prd.md §5.5.
 *
 * Phase 27 adds an optional AI-enhanced path (gated by the caller on
 * premium entitlement -- this class has no idea what "premium" means).
 * Falls back to templates on ANY problem: no provider configured, a
 * failed call, malformed JSON, or output that fails validation.
 *
 * Phase 29 adds an interactive refine follow-up ("make it shorter") on
 * top of an AI-generated result -- see RefineSession/RefineSessionStore.
 * Unlike generate(), refine() has no template fallback (there's nothing
 * to "refine" about a template) so it throws a clear error instead of
 * silently degrading.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DescriptionGeneratorService {

    private static final int MAX_HASHTAGS = 15;
    /** YouTube's own description character limit. */
    private static final int MAX_DESCRIPTION_LENGTH = 5000;
    private static final int AI_MAX_OUTPUT_TOKENS = 3000;
    private static final Pattern NON_WORD = Pattern.compile("[^A-Za-z0-9 ]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private static final String SYSTEM_PROMPT = """
        You write YouTube video descriptions for gaming content \
        creators. Reply with ONLY a JSON object, no markdown \
        formatting, no commentary: {"description": "a friendly, \
        SEO-aware description, 5000 characters or fewer, that \
        naturally weaves in the channel name and any social links \
        given", "seoKeywordsSection": "a short comma-separated \
        keyword list", "hashtags": ["1 to 15 hashtags, each starting \
        with #, no spaces"]}. Never use unverifiable absolute claims \
        like "world record", "best ever", "#1 in the world", or \
        "greatest of all time".""";

    private final TextSanitizer sanitizer;
    private final Optional<AiGenerationProvider> aiProvider;
    private final ObjectMapper objectMapper;
    private final AiUsageGuard usageGuard;
    private final RefineSessionStore refineSessionStore;

    public DescriptionGeneratorResponse generate(DescriptionGeneratorRequest request) {
        return generate(request, false, null);
    }

    /**
     * useAi is decided by the caller (premium entitlement check happens
     * in the controller); userId is required whenever useAi is true --
     * it's how Phase 28's rate limit/usage log and Phase 29's refine
     * session attribute the call.
     */
    public DescriptionGeneratorResponse generate(DescriptionGeneratorRequest request, boolean useAi, UUID userId) {
        if (useAi && aiProvider.isPresent()) {
            AiGenerationProvider provider = aiProvider.get();
            String denialReason = usageGuard.tryAcquire(userId);
            if (denialReason == null) {
                long started = System.currentTimeMillis();
                try {
                    AiCallResult outcome = generateWithAi(request, provider, List.of());
                    usageGuard.recordSuccess(userId, ToolType.DESCRIPTION_GENERATOR, provider.getProviderName(),
                        provider.getModelName(), outcome.servedBy(), outcome.inputTokens(), outcome.outputTokens(),
                        System.currentTimeMillis() - started);
                    RefineSession session = refineSessionStore.create(
                        userId, ToolType.DESCRIPTION_GENERATOR, SYSTEM_PROMPT, outcome.rawJsonText());
                    return outcome.response().toBuilder().refineSessionId(session.getId().toString()).build();
                } catch (Exception e) {
                    usageGuard.recordFailure(userId, ToolType.DESCRIPTION_GENERATOR, provider.getProviderName(),
                        e.getClass().getSimpleName(), System.currentTimeMillis() - started);
                    log.warn("AI description generation failed, falling back to templates: {}", e.getClass().getSimpleName());
                }
            } else {
                usageGuard.recordThrottled(userId, ToolType.DESCRIPTION_GENERATOR, provider.getProviderName(), denialReason);
            }
        }
        return generateFromTemplates(request);
    }

    /**
     * "make it shorter" on top of an earlier AI generation. No
     * template fallback exists for this -- any problem throws a clear
     * BusinessRuleViolationException instead of degrading silently.
     */
    public DescriptionGeneratorResponse refine(String sessionIdRaw, String message, UUID userId) {
        UUID sessionId = parseSessionId(sessionIdRaw);
        RefineSession session = refineSessionStore.find(sessionId, userId, ToolType.DESCRIPTION_GENERATOR)
            .orElseThrow(() -> new BusinessRuleViolationException(
                "This refine session has expired or wasn't found -- generate a new result to keep refining."));
        if (session.getTurnCount() >= RefineSessionStore.MAX_REFINE_TURNS) {
            throw new BusinessRuleViolationException(
                "This refine session has reached its limit -- generate a new result to keep refining.");
        }
        AiGenerationProvider provider = aiProvider.orElseThrow(
            () -> new BusinessRuleViolationException("AI generation isn't available right now."));

        String denialReason = usageGuard.tryAcquire(userId);
        if (denialReason != null) {
            usageGuard.recordThrottled(userId, ToolType.DESCRIPTION_GENERATOR, provider.getProviderName(), denialReason);
            throw new BusinessRuleViolationException("Too many AI requests right now -- please try again in a moment.");
        }

        String sanitizedMessage = sanitizer.sanitize(message);
        String refinePrompt = "Refine the previous description using this instruction: " + sanitizedMessage
            + "\n\nReply with the SAME JSON shape as before.";

        long started = System.currentTimeMillis();
        try {
            AiGenerationResult result = provider.generate(new AiGenerationRequest(
                session.getSystemPrompt(), session.historySnapshot(), refinePrompt, AI_MAX_OUTPUT_TOKENS));
            String cleaned = stripCodeFences(result.text());
            ValidatedDescription validated = validateParsed(parseAiDescription(cleaned));

            usageGuard.recordSuccess(userId, ToolType.DESCRIPTION_GENERATOR, provider.getProviderName(),
                provider.getModelName(), result.servedBy(), result.inputTokens(), result.outputTokens(),
                System.currentTimeMillis() - started);
            session.recordExchange(sanitizedMessage, cleaned);

            return DescriptionGeneratorResponse.builder()
                .description(validated.description())
                .seoKeywordsSection(validated.seoKeywordsSection())
                .hashtags(validated.hashtags())
                .refineSessionId(session.getId().toString())
                .build();
        } catch (Exception e) {
            usageGuard.recordFailure(userId, ToolType.DESCRIPTION_GENERATOR, provider.getProviderName(),
                e.getClass().getSimpleName(), System.currentTimeMillis() - started);
            throw new BusinessRuleViolationException("Couldn't refine that result -- please try again.");
        }
    }

    private UUID parseSessionId(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleViolationException(
                "This refine session has expired or wasn't found -- generate a new result to keep refining.");
        }
    }

    private record AiCallResult(DescriptionGeneratorResponse response, long inputTokens, long outputTokens,
            String servedBy, String rawJsonText) {
    }

    private AiCallResult generateWithAi(
            DescriptionGeneratorRequest request, AiGenerationProvider provider, List<ConversationTurn> history) {
        String game = sanitizer.sanitize(request.getGame());
        String topic = sanitizer.sanitize(request.getTopic());
        String channelName = sanitizer.sanitize(request.getChannelName());
        List<String> keywords = (request.getKeywords() == null ? List.<String>of() : request.getKeywords())
            .stream().map(sanitizer::sanitize).toList();
        List<SocialLinkRequest> socialLinks =
            request.getSocialLinks() == null ? List.of() : request.getSocialLinks();

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("Game: ").append(game).append('\n');
        userPrompt.append("Topic: ").append(topic).append('\n');
        userPrompt.append("Channel name: ").append(channelName).append('\n');
        userPrompt.append("Keywords: ").append(keywords.isEmpty() ? "(none)" : String.join(", ", keywords)).append('\n');
        if (!socialLinks.isEmpty()) {
            userPrompt.append("Social links:\n");
            for (SocialLinkRequest link : socialLinks) {
                userPrompt.append(sanitizer.sanitize(link.getPlatform())).append(": ").append(link.getUrl()).append('\n');
            }
        }

        AiGenerationResult result = provider.generate(
            new AiGenerationRequest(SYSTEM_PROMPT, history, userPrompt.toString(), AI_MAX_OUTPUT_TOKENS));
        String cleaned = stripCodeFences(result.text());
        ValidatedDescription validated = validateParsed(parseAiDescription(cleaned));

        DescriptionGeneratorResponse response = DescriptionGeneratorResponse.builder()
            .description(validated.description())
            .seoKeywordsSection(validated.seoKeywordsSection())
            .hashtags(validated.hashtags())
            .build();
        return new AiCallResult(response, result.inputTokens(), result.outputTokens(), result.servedBy(), cleaned);
    }

    private record AiDescription(String description, String seoKeywordsSection, List<String> hashtags) {
    }

    private record ValidatedDescription(String description, String seoKeywordsSection, List<String> hashtags) {
    }

    private AiDescription parseAiDescription(String cleanedText) {
        try {
            return objectMapper.readValue(cleanedText, AiDescription.class);
        } catch (Exception e) {
            throw new AiGenerationException("Malformed AI description JSON.", e);
        }
    }

    private ValidatedDescription validateParsed(AiDescription parsed) {
        String description = validateDescription(parsed.description());
        List<String> hashtags = validateHashtags(parsed.hashtags());
        String seoKeywordsSection = parsed.seoKeywordsSection() == null ? "" : parsed.seoKeywordsSection().trim();
        if (description == null || hashtags.isEmpty()) {
            throw new AiGenerationException("AI description output failed validation.");
        }
        return new ValidatedDescription(description, seoKeywordsSection, hashtags);
    }

    private String validateDescription(String description) {
        if (description == null) return null;
        String trimmed = description.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_DESCRIPTION_LENGTH) return null;
        if (BannedAbsoluteClaims.containsBannedClaim(trimmed)) return null;
        return trimmed;
    }

    private List<String> validateHashtags(List<String> hashtags) {
        if (hashtags == null) return List.of();
        LinkedHashSet<String> valid = new LinkedHashSet<>();
        for (String tag : hashtags) {
            if (tag == null) continue;
            String trimmed = tag.trim();
            if (!trimmed.startsWith("#") || trimmed.contains(" ") || trimmed.length() <= 1) continue;
            valid.add(trimmed);
        }
        List<String> capped = new ArrayList<>(valid);
        return capped.size() > MAX_HASHTAGS ? capped.subList(0, MAX_HASHTAGS) : capped;
    }

    private String stripCodeFences(String text) {
        return text.replaceAll("(?s)^\\s*```(?:json)?", "").replaceAll("```\\s*$", "").trim();
    }

    private DescriptionGeneratorResponse generateFromTemplates(DescriptionGeneratorRequest request) {
        String game = sanitizer.sanitize(request.getGame());
        String topic = sanitizer.sanitize(request.getTopic());
        String channelName = sanitizer.sanitize(request.getChannelName());
        List<String> keywords = (request.getKeywords() == null ? List.<String>of() : request.getKeywords())
            .stream().map(sanitizer::sanitize).toList();

        String description = buildDescription(request, game, topic, channelName, keywords);
        String seoKeywordsSection = buildSeoKeywordsSection(game, keywords);
        List<String> hashtags = buildHashtags(game, topic, keywords);

        return DescriptionGeneratorResponse.builder()
            .description(description)
            .seoKeywordsSection(seoKeywordsSection)
            .hashtags(hashtags)
            .build();
    }

    private String buildDescription(
            DescriptionGeneratorRequest request, String game, String topic, String channelName,
            List<String> keywords) {
        StringBuilder sb = new StringBuilder();
        sb.append("In this video, ").append(channelName).append(" covers ")
            .append(topic).append(" in ").append(game).append(".\n\n");

        List<SocialLinkRequest> socialLinks =
            request.getSocialLinks() == null ? List.of() : request.getSocialLinks();
        if (!socialLinks.isEmpty()) {
            sb.append("Follow ").append(channelName).append(":\n");
            for (SocialLinkRequest link : socialLinks) {
                sb.append(sanitizer.sanitize(link.getPlatform())).append(": ")
                    .append(link.getUrl()).append('\n');
            }
            sb.append('\n');
        }

        if (!keywords.isEmpty()) {
            sb.append("Keywords: ").append(String.join(", ", keywords)).append("\n\n");
        }

        sb.append(String.join(" ", buildHashtags(game, topic, keywords)));
        return sb.toString().trim();
    }

    private String buildSeoKeywordsSection(String game, List<String> keywords) {
        LinkedHashSet<String> all = new LinkedHashSet<>(keywords);
        all.add(game + " gameplay");
        return String.join(", ", all);
    }

    private List<String> buildHashtags(String game, String topic, List<String> keywords) {
        LinkedHashSet<String> hashtags = new LinkedHashSet<>();
        hashtags.add(toHashtag(game));
        hashtags.add(toHashtag(topic));
        for (String keyword : keywords) {
            hashtags.add(toHashtag(keyword));
        }
        hashtags.remove(null);
        hashtags.remove("#");

        List<String> capped = new ArrayList<>(hashtags);
        return capped.size() > MAX_HASHTAGS ? capped.subList(0, MAX_HASHTAGS) : capped;
    }

    private String toHashtag(String phrase) {
        if (phrase == null || phrase.isBlank()) return null;
        String cleaned = WHITESPACE.matcher(NON_WORD.matcher(phrase).replaceAll(" ")).replaceAll(" ").trim();
        if (cleaned.isEmpty()) return null;
        StringBuilder sb = new StringBuilder("#");
        for (String word : cleaned.split(" ")) {
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.toString();
    }
}
