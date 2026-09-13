package com.extremis.hub.tools.title;

import com.extremis.hub.tools.common.TextSanitizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Pure template composition -- zero AI inference cost. See
 * docs/06-prd.md §5.4. Every template here is reviewed to never
 * contain an unsubstantiated absolute claim (see
 * TitleGeneratorServiceTest.noTemplateContainsBannedAbsoluteClaims) --
 * there is no runtime "claim filter" because nothing in the template
 * bank generates such claims in the first place.
 */
@Service
@RequiredArgsConstructor
public class TitleGeneratorService {

    private static final int YOUTUBE_TITLE_MAX_LENGTH = 100;

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

    public TitleGeneratorResponse generate(TitleGeneratorRequest request) {
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
