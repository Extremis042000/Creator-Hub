package com.extremis.hub.tools.description;

import com.extremis.hub.tools.common.TextSanitizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Template composition -- zero AI inference cost. See docs/06-prd.md §5.5. */
@Service
@RequiredArgsConstructor
public class DescriptionGeneratorService {

    private static final int MAX_HASHTAGS = 15;
    private static final Pattern NON_WORD = Pattern.compile("[^A-Za-z0-9 ]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final TextSanitizer sanitizer;

    public DescriptionGeneratorResponse generate(DescriptionGeneratorRequest request) {
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
