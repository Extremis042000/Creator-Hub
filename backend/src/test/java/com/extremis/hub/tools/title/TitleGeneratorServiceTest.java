package com.extremis.hub.tools.title;

import static org.assertj.core.api.Assertions.assertThat;

import com.extremis.hub.tools.common.TextSanitizer;
import java.util.List;
import org.junit.jupiter.api.Test;

class TitleGeneratorServiceTest {

    private final TitleGeneratorService service = new TitleGeneratorService(new TextSanitizer());

    private TitleGeneratorRequest request(VideoType videoType, Tone tone, List<String> keywords) {
        TitleGeneratorRequest r = new TitleGeneratorRequest();
        r.setGame("Valorant");
        r.setTopic("1v5 clutch on Ascent");
        r.setVideoType(videoType);
        r.setTone(tone);
        r.setKeywords(keywords);
        return r;
    }

    @Test
    void generatesAtLeastFiveLongFormAndThreeShortForm_allUnique() {
        TitleGeneratorResponse response = service.generate(request(VideoType.HIGHLIGHT, Tone.HYPE, List.of("ace")));

        assertThat(response.getTitles()).hasSizeGreaterThanOrEqualTo(5);
        assertThat(response.getTitles()).doesNotHaveDuplicates();
        assertThat(response.getShortFormTitles()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(response.getShortFormTitles()).doesNotHaveDuplicates();
    }

    @Test
    void noTitleExceedsYouTubeLengthLimit() {
        TitleGeneratorRequest request = request(VideoType.TUTORIAL, Tone.COMPETITIVE, List.of());
        request.setGame("A".repeat(100));
        request.setTopic("B".repeat(100));

        TitleGeneratorResponse response = service.generate(request);

        assertThat(response.getTitles()).allSatisfy(title -> assertThat(title.length()).isLessThanOrEqualTo(100));
        assertThat(response.getShortFormTitles())
            .allSatisfy(title -> assertThat(title.length()).isLessThanOrEqualTo(100));
    }

    @Test
    void sanitizesHtmlAndControlCharactersFromInput() {
        TitleGeneratorRequest request = request(VideoType.MONTAGE, Tone.FUNNY, List.of());
        request.setGame("Valorant<script>alert(1)</script>");
        request.setTopic("clutch   moment"); // extra whitespace

        TitleGeneratorResponse response = service.generate(request);

        assertThat(response.getTitles()).noneMatch(t -> t.contains("<script>"));
        assertThat(response.getTitles()).anyMatch(t -> t.contains("clutch moment"));
    }

    @Test
    void noTemplateContainsBannedAbsoluteClaims() {
        List<String> banned = List.of("WORLD RECORD", "#1 IN THE WORLD", "BEST EVER", "GREATEST OF ALL TIME");
        for (VideoType videoType : VideoType.values()) {
            for (Tone tone : Tone.values()) {
                TitleGeneratorResponse response = service.generate(request(videoType, tone, List.of()));
                for (String title : response.getTitles()) {
                    String upper = title.toUpperCase();
                    assertThat(banned).noneMatch(upper::contains);
                }
            }
        }
    }
}
