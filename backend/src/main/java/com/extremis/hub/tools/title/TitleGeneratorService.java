package com.extremis.hub.tools.title;

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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Template composition remains the free-tier baseline -- zero AI
 * inference cost, every template reviewed to never contain an
 * unsubstantiated absolute claim (see
 * TitleGeneratorServiceTest.noTemplateContainsBannedAbsoluteClaims).
 * See docs/06-prd.md §5.4.
 *
 * Phase 27 adds an optional AI-enhanced path (gated by the caller on
 * premium entitlement -- this class has no idea what "premium" means).
 * AI output isn't statically auditable like the template bank, so it's
 * validated at runtime (length caps, banned absolute claims) and falls
 * back to templates on ANY problem: no provider configured, a failed
 * call, malformed JSON, or output that fails validation. A premium
 * user must never see an error where a free user would have gotten a
 * title.
 *
 * Phase 29 adds an interactive refine follow-up ("make it punchier") on
 * top of an AI-generated result -- see RefineSession/RefineSessionStore.
 * Unlike generate(), refine() has no template fallback (there's nothing
 * to "refine" about a template) so it throws a clear error instead of
 * silently degrading.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TitleGeneratorService {

    private static final int YOUTUBE_TITLE_MAX_LENGTH = 100;
    private static final int SHORT_FORM_TITLE_MAX_LENGTH = 40;
    // Phase 36 (hillclimb v2): raised from 3000 -- v1's added instructions
    // increased how often the free-tier model's hidden reasoning consumed
    // the whole budget before emitting visible text (finish_reason=length
    // on 3/24 eval attempts). 4500 eliminated that failure mode with no
    // quality regression. See backend/.claude/hillclimb/ai-title-description/.
    private static final int AI_MAX_OUTPUT_TOKENS = 4500;

    // Phase 36 (hillclimb v2, measured quality 1.958 -> 3.0/3.0, 24/24
    // valid): the last two sentences target two real findings from the
    // Phase 35 eval -- titles reordering the same words instead of varying
    // the angle, and inconsistent keyword-stuffing when many keywords are
    // supplied. See backend/.claude/hillclimb/ai-title-description/report.html.
    private static final String SYSTEM_PROMPT = """
        You write YouTube titles for gaming content creators. Reply with \
        ONLY a JSON object, no markdown formatting, no commentary: \
        {"titles": [5 to 8 long-form titles, each 100 characters or \
        fewer], "shortFormTitles": [3 to 5 short titles, each 40 \
        characters or fewer]}. Never use unverifiable absolute claims \
        like "world record", "best ever", "#1 in the world", or \
        "greatest of all time". Vary each title's structure, not just \
        its word order: give each one a different opening hook (a \
        direct statement, a question, a "how I/how to" framing, a \
        bracketed qualifier like "(No Damage)" or "(Solo)", or a number \
        where it fits naturally) so the options read as genuinely \
        different titles, not the same sentence reordered. If more than \
        4 keywords are given, use only the 3-4 most relevant to each \
        individual title rather than cramming all of them into every \
        one.""";

    private static final Map<Tone, String> TONE_WORDS = Map.of(
        Tone.HYPE, "INSANE",
        Tone.CASUAL, "Chill",
        Tone.COMPETITIVE, "Ranked",
        Tone.FUNNY, "Hilarious"
    );

    private static final Map<VideoType, List<String>> VIDEO_TYPE_TEMPLATES = Map.of(
        VideoType.HIGHLIGHT, List.of(
            "{toneWord} {game} Highlight: {topic}",
            "{game} {topic} — {toneWord} Moment",
            "This {game} {topic} Was {toneWord}"),
        VideoType.TUTORIAL, List.of(
            "{game} {topic} Guide ({toneWord})",
            "How To {topic} in {game}",
            "{topic} Tutorial — {game} Tips"),
        VideoType.MONTAGE, List.of(
            "{game} {topic} Montage — {toneWord}",
            "{toneWord} {game} Plays: {topic}",
            "{topic} Montage | {game}"),
        VideoType.VLOG, List.of(
            "{game} Vlog: {topic}",
            "A Day of {game} — {topic}",
            "{toneWord} {game} Vlog: {topic}"),
        VideoType.LIVESTREAM_RECAP, List.of(
            "{game} Stream Recap: {topic}",
            "Best Moments From My {game} Stream — {topic}",
            "{toneWord} {game} Stream Recap: {topic}")
    );

    private static final List<String> GENERIC_TEMPLATES = List.of(
        "{game} {topic} ({toneWord})",
        "{topic} in {game} — {toneWord}"
    );

    private static final List<String> SHORT_FORM_TEMPLATES = List.of(
        "{toneWord} {game}!",
        "{topic} in {game}",
        "{game}: {topic}"
    );

    private final TextSanitizer sanitizer;
    private final Optional<AiGenerationProvider> aiProvider;
    private final ObjectMapper objectMapper;
    private final AiUsageGuard usageGuard;
    private final RefineSessionStore refineSessionStore;

    public TitleGeneratorResponse generate(TitleGeneratorRequest request) {
        return generate(request, false, null);
    }

    /**
     * useAi is decided by the caller (premium entitlement check happens
     * in the controller); userId is required whenever useAi is true --
     * it's how Phase 28's rate limit/usage log and Phase 29's refine
     * session attribute the call.
     */
    public TitleGeneratorResponse generate(TitleGeneratorRequest request, boolean useAi, UUID userId) {
        if (useAi && aiProvider.isPresent()) {
            AiGenerationProvider provider = aiProvider.get();
            String denialReason = usageGuard.tryAcquire(userId);
            if (denialReason == null) {
                long started = System.currentTimeMillis();
                try {
                    AiCallResult outcome = generateWithAi(request, provider, List.of());
                    usageGuard.recordSuccess(userId, ToolType.TITLE_GENERATOR, provider.getProviderName(),
                        provider.getModelName(), outcome.servedBy(), outcome.inputTokens(), outcome.outputTokens(),
                        System.currentTimeMillis() - started);
                    RefineSession session = refineSessionStore.create(
                        userId, ToolType.TITLE_GENERATOR, SYSTEM_PROMPT, outcome.rawJsonText());
                    return outcome.response().toBuilder().refineSessionId(session.getId().toString()).build();
                } catch (Exception e) {
                    usageGuard.recordFailure(userId, ToolType.TITLE_GENERATOR, provider.getProviderName(),
                        e.getClass().getSimpleName(), System.currentTimeMillis() - started);
                    log.warn("AI title generation failed, falling back to templates: {}", e.getClass().getSimpleName());
                }
            } else {
                usageGuard.recordThrottled(userId, ToolType.TITLE_GENERATOR, provider.getProviderName(), denialReason);
            }
        }
        return generateFromTemplates(request);
    }

    /**
     * "make it punchier" on top of an earlier AI generation. No
     * template fallback exists for this -- any problem throws a clear
     * BusinessRuleViolationException instead of degrading silently.
     */
    public TitleGeneratorResponse refine(String sessionIdRaw, String message, UUID userId) {
        UUID sessionId = parseSessionId(sessionIdRaw);
        RefineSession session = refineSessionStore.find(sessionId, userId, ToolType.TITLE_GENERATOR)
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
            usageGuard.recordThrottled(userId, ToolType.TITLE_GENERATOR, provider.getProviderName(), denialReason);
            throw new BusinessRuleViolationException("Too many AI requests right now -- please try again in a moment.");
        }

        String sanitizedMessage = sanitizer.sanitize(message);
        String refinePrompt = "Refine the previous titles using this instruction: " + sanitizedMessage
            + "\n\nReply with the SAME JSON shape as before.";

        long started = System.currentTimeMillis();
        try {
            AiGenerationResult result = provider.generate(new AiGenerationRequest(
                session.getSystemPrompt(), session.historySnapshot(), refinePrompt, AI_MAX_OUTPUT_TOKENS));
            String cleaned = stripCodeFences(result.text());
            ValidatedTitles validated = validateParsed(parseAiTitles(cleaned));

            usageGuard.recordSuccess(userId, ToolType.TITLE_GENERATOR, provider.getProviderName(),
                provider.getModelName(), result.servedBy(), result.inputTokens(), result.outputTokens(),
                System.currentTimeMillis() - started);
            session.recordExchange(sanitizedMessage, cleaned);

            return TitleGeneratorResponse.builder()
                .titles(validated.titles())
                .shortFormTitles(validated.shortFormTitles())
                .refineSessionId(session.getId().toString())
                .build();
        } catch (Exception e) {
            usageGuard.recordFailure(userId, ToolType.TITLE_GENERATOR, provider.getProviderName(),
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

    private record AiCallResult(TitleGeneratorResponse response, long inputTokens, long outputTokens,
            String servedBy, String rawJsonText) {
    }

    private AiCallResult generateWithAi(
            TitleGeneratorRequest request, AiGenerationProvider provider, List<ConversationTurn> history) {
        String game = sanitizer.sanitize(request.getGame());
        String topic = sanitizer.sanitize(request.getTopic());
        List<String> keywords = request.getKeywords() == null ? List.of() :
            request.getKeywords().stream().map(sanitizer::sanitize).toList();

        String userPrompt = "Game: %s\nTopic: %s\nVideo type: %s\nTone: %s\nKeywords: %s".formatted(
            game, topic, request.getVideoType(), request.getTone(),
            keywords.isEmpty() ? "(none)" : String.join(", ", keywords));

        AiGenerationResult result = provider.generate(
            new AiGenerationRequest(SYSTEM_PROMPT, history, userPrompt, AI_MAX_OUTPUT_TOKENS));
        String cleaned = stripCodeFences(result.text());
        ValidatedTitles validated = validateParsed(parseAiTitles(cleaned));

        TitleGeneratorResponse response = TitleGeneratorResponse.builder()
            .titles(validated.titles())
            .shortFormTitles(validated.shortFormTitles())
            .build();
        return new AiCallResult(response, result.inputTokens(), result.outputTokens(), result.servedBy(), cleaned);
    }

    private record AiTitles(List<String> titles, List<String> shortFormTitles) {
    }

    private record ValidatedTitles(List<String> titles, List<String> shortFormTitles) {
    }

    private AiTitles parseAiTitles(String cleanedText) {
        try {
            return objectMapper.readValue(cleanedText, AiTitles.class);
        } catch (Exception e) {
            throw new AiGenerationException("Malformed AI title JSON.", e);
        }
    }

    private ValidatedTitles validateParsed(AiTitles parsed) {
        List<String> titles = validateTitles(parsed.titles(), YOUTUBE_TITLE_MAX_LENGTH);
        List<String> shortFormTitles = validateTitles(parsed.shortFormTitles(), SHORT_FORM_TITLE_MAX_LENGTH);
        if (titles.size() < 3 || shortFormTitles.isEmpty()) {
            throw new AiGenerationException("AI title output failed validation.");
        }
        return new ValidatedTitles(titles, shortFormTitles);
    }

    private List<String> validateTitles(List<String> titles, int maxLength) {
        if (titles == null) return List.of();
        LinkedHashSet<String> valid = new LinkedHashSet<>();
        for (String title : titles) {
            if (title == null) continue;
            String trimmed = title.trim();
            if (trimmed.isEmpty() || trimmed.length() > maxLength) continue;
            if (BannedAbsoluteClaims.containsBannedClaim(trimmed)) continue;
            valid.add(trimmed);
        }
        return List.copyOf(valid);
    }

    private String stripCodeFences(String text) {
        return text.replaceAll("(?s)^\\s*```(?:json)?", "").replaceAll("```\\s*$", "").trim();
    }

    private TitleGeneratorResponse generateFromTemplates(TitleGeneratorRequest request) {
        String game = sanitizer.sanitize(request.getGame());
        String topic = sanitizer.sanitize(request.getTopic());
        String toneWord = TONE_WORDS.get(request.getTone());
        List<String> keywords = request.getKeywords() == null ? List.of() : request.getKeywords();

        Map<String, String> slots = Map.of("game", game, "topic", topic, "toneWord", toneWord);

        List<String> longFormTemplates = new ArrayList<>(VIDEO_TYPE_TEMPLATES.get(request.getVideoType()));
        longFormTemplates.addAll(GENERIC_TEMPLATES);
        if (!keywords.isEmpty()) {
            String keyword = sanitizer.sanitize(keywords.get(0));
            longFormTemplates.add("{game} {topic} — " + keyword);
        }

        List<String> titles = render(longFormTemplates, slots);
        List<String> shortFormTitles = render(SHORT_FORM_TEMPLATES, slots);

        return TitleGeneratorResponse.builder()
            .titles(titles)
            .shortFormTitles(shortFormTitles)
            .build();
    }

    private List<String> render(List<String> templates, Map<String, String> slots) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String template : templates) {
            String filled = template
                .replace("{game}", slots.get("game"))
                .replace("{topic}", slots.get("topic"))
                .replace("{toneWord}", slots.get("toneWord"));
            unique.add(capLength(filled));
        }
        return List.copyOf(unique);
    }

    private String capLength(String title) {
        if (title.length() <= YOUTUBE_TITLE_MAX_LENGTH) {
            return title;
        }
        return title.substring(0, YOUTUBE_TITLE_MAX_LENGTH - 3) + "...";
    }
}
