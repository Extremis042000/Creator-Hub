package com.extremis.hub.tools.description;

import static org.assertj.core.api.Assertions.assertThat;

import com.extremis.hub.tools.common.TextSanitizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DescriptionGeneratorServiceTest {

    private final DescriptionGeneratorService service =
        new DescriptionGeneratorService(new TextSanitizer(), Optional.empty(), new ObjectMapper());

    @Test
    void includesChannelNameAndSocialLinks() {
        DescriptionGeneratorRequest request = new DescriptionGeneratorRequest();
        request.setGame("BGMI");
        request.setTopic("Solo vs Squad chicken dinner");
        request.setChannelName("EXTREMIS Plays");
        request.setKeywords(List.of("bgmi highlights", "solo vs squad"));
        SocialLinkRequest link = new SocialLinkRequest();
        link.setPlatform("YouTube");
        link.setUrl("https://youtube.com/@extremisplays");
        request.setSocialLinks(List.of(link));

        DescriptionGeneratorResponse response = service.generate(request);

        assertThat(response.getDescription()).contains("EXTREMIS Plays");
        assertThat(response.getDescription()).contains("https://youtube.com/@extremisplays");
        assertThat(response.getHashtags()).doesNotHaveDuplicates();
        assertThat(response.getHashtags().size()).isLessThanOrEqualTo(15);
    }

    @Test
    void hashtagsAreDeduplicatedAndCappedAtFifteen() {
        DescriptionGeneratorRequest request = new DescriptionGeneratorRequest();
        request.setGame("Valorant");
        request.setTopic("Valorant"); // deliberately duplicate of game
        request.setChannelName("Test Channel");
        request.setKeywords(List.of(
            "one", "two", "three", "four", "five", "six", "seven", "eight",
            "nine", "ten", "eleven", "twelve", "thirteen", "fourteen"));

        DescriptionGeneratorResponse response = service.generate(request);

        assertThat(response.getHashtags()).doesNotHaveDuplicates();
        assertThat(response.getHashtags().size()).isLessThanOrEqualTo(15);
        assertThat(response.getHashtags()).contains("#Valorant");
    }

    @Test
    void validHttpsUrlValidator_rejectsNonHttpsAndMalformed() {
        ValidHttpsUrlValidator validator = new ValidHttpsUrlValidator();
        assertThat(validator.isValid("https://example.com", null)).isTrue();
        assertThat(validator.isValid("http://example.com", null)).isFalse();
        assertThat(validator.isValid("not-a-url", null)).isFalse();
        assertThat(validator.isValid("", null)).isFalse();
        assertThat(validator.isValid(null, null)).isFalse();
    }
}
