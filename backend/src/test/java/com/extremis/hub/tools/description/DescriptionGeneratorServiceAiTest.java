package com.extremis.hub.tools.description;

import static org.assertj.core.api.Assertions.assertThat;

import com.extremis.hub.ai.AiGenerationException;
import com.extremis.hub.ai.AiGenerationProvider;
import com.extremis.hub.ai.AiGenerationRequest;
import com.extremis.hub.ai.AiGenerationResult;
import com.extremis.hub.tools.common.TextSanitizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Phase 27: the AI-enhanced path and its template fallback -- the deterministic-only path stays covered by DescriptionGeneratorServiceTest. */
class DescriptionGeneratorServiceAiTest {

    private DescriptionGeneratorRequest request() {
        DescriptionGeneratorRequest r = new DescriptionGeneratorRequest();
        r.setGame("BGMI");
        r.setTopic("Solo vs Squad chicken dinner");
        r.setChannelName("EXTREMIS Plays");
        r.setKeywords(List.of("bgmi highlights"));
        return r;
    }

    private AiGenerationProvider fakeProvider(String jsonText) {
        return new AiGenerationProvider() {
            @Override public String getProviderName() { return "fake"; }
            @Override public String getModelName() { return "fake-model"; }
            @Override public AiGenerationResult generate(AiGenerationRequest request) {
                return new AiGenerationResult(jsonText, 10, 20, "fake-model");
            }
        };
    }

    private AiGenerationProvider failingProvider() {
        return new AiGenerationProvider() {
            @Override public String getProviderName() { return "fake"; }
            @Override public String getModelName() { return "fake-model"; }
            @Override public AiGenerationResult generate(AiGenerationRequest request) {
                throw new AiGenerationException("simulated failure");
            }
        };
    }

    @Test
    void useAiTrueWithValidJsonReturnsAiGeneratedDescription() {
        String json = """
            {"description": "A great BGMI solo vs squad chicken dinner video.", \
            "seoKeywordsSection": "bgmi, chicken dinner, solo vs squad", \
            "hashtags": ["#BGMI", "#ChickenDinner"]}""";
        DescriptionGeneratorService service = new DescriptionGeneratorService(
            new TextSanitizer(), Optional.of(fakeProvider(json)), new ObjectMapper());

        DescriptionGeneratorResponse response = service.generate(request(), true);

        assertThat(response.getDescription()).isEqualTo("A great BGMI solo vs squad chicken dinner video.");
        assertThat(response.getHashtags()).containsExactly("#BGMI", "#ChickenDinner");
    }

    @Test
    void useAiFalseNeverCallsProviderAndUsesTemplates() {
        DescriptionGeneratorService service = new DescriptionGeneratorService(
            new TextSanitizer(), Optional.of(failingProvider()), new ObjectMapper());

        DescriptionGeneratorResponse response = service.generate(request(), false);

        assertThat(response.getDescription()).contains("EXTREMIS Plays");
    }

    @Test
    void providerFailureFallsBackToTemplates() {
        DescriptionGeneratorService service = new DescriptionGeneratorService(
            new TextSanitizer(), Optional.of(failingProvider()), new ObjectMapper());

        DescriptionGeneratorResponse response = service.generate(request(), true);

        assertThat(response.getDescription()).contains("EXTREMIS Plays");
    }

    @Test
    void malformedJsonFallsBackToTemplates() {
        DescriptionGeneratorService service = new DescriptionGeneratorService(
            new TextSanitizer(), Optional.of(fakeProvider("not json at all")), new ObjectMapper());

        DescriptionGeneratorResponse response = service.generate(request(), true);

        assertThat(response.getDescription()).contains("EXTREMIS Plays");
    }

    @Test
    void bannedClaimInDescriptionFallsBackToTemplates() {
        String json = """
            {"description": "This is the best ever BGMI video, a world record run.", \
            "seoKeywordsSection": "bgmi", "hashtags": ["#BGMI"]}""";
        DescriptionGeneratorService service = new DescriptionGeneratorService(
            new TextSanitizer(), Optional.of(fakeProvider(json)), new ObjectMapper());

        DescriptionGeneratorResponse response = service.generate(request(), true);

        assertThat(response.getDescription()).doesNotContainIgnoringCase("best ever");
        assertThat(response.getDescription()).contains("EXTREMIS Plays");
    }

    @Test
    void noHashtagsFallsBackToTemplates() {
        String json = """
            {"description": "A fine description with no hashtags supplied at all here.", \
            "seoKeywordsSection": "bgmi", "hashtags": []}""";
        DescriptionGeneratorService service = new DescriptionGeneratorService(
            new TextSanitizer(), Optional.of(fakeProvider(json)), new ObjectMapper());

        DescriptionGeneratorResponse response = service.generate(request(), true);

        assertThat(response.getHashtags()).isNotEmpty();
        assertThat(response.getDescription()).contains("EXTREMIS Plays");
    }

    @Test
    void noProviderConfiguredFallsBackToTemplatesEvenWithUseAiTrue() {
        DescriptionGeneratorService service = new DescriptionGeneratorService(
            new TextSanitizer(), Optional.empty(), new ObjectMapper());

        DescriptionGeneratorResponse response = service.generate(request(), true);

        assertThat(response.getDescription()).contains("EXTREMIS Plays");
    }
}
