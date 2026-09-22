package com.extremis.hub.tools.title;

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

/** Phase 27: the AI-enhanced path and its template fallback -- the deterministic-only path stays covered by TitleGeneratorServiceTest. */
class TitleGeneratorServiceAiTest {

    private TitleGeneratorRequest request() {
        TitleGeneratorRequest r = new TitleGeneratorRequest();
        r.setGame("Valorant");
        r.setTopic("1v5 clutch on Ascent");
        r.setVideoType(VideoType.HIGHLIGHT);
        r.setTone(Tone.HYPE);
        r.setKeywords(List.of("ace"));
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
    void useAiTrueWithValidJsonReturnsAiGeneratedTitles() {
        String json = """
            {"titles": ["My Great Valorant Clutch", "Another Good Title", \
            "Third Title Here", "Fourth One Too", "Fifth One As Well"], \
            "shortFormTitles": ["Short One", "Short Two", "Short Three"]}""";
        TitleGeneratorService service = new TitleGeneratorService(
            new TextSanitizer(), Optional.of(fakeProvider(json)), new ObjectMapper());

        TitleGeneratorResponse response = service.generate(request(), true);

        assertThat(response.getTitles()).contains("My Great Valorant Clutch");
        assertThat(response.getShortFormTitles()).contains("Short One");
    }

    @Test
    void useAiFalseNeverCallsProviderAndUsesTemplates() {
        TitleGeneratorService service = new TitleGeneratorService(
            new TextSanitizer(), Optional.of(failingProvider()), new ObjectMapper());

        TitleGeneratorResponse response = service.generate(request(), false);

        assertThat(response.getTitles()).hasSizeGreaterThanOrEqualTo(5);
    }

    @Test
    void providerFailureFallsBackToTemplates() {
        TitleGeneratorService service = new TitleGeneratorService(
            new TextSanitizer(), Optional.of(failingProvider()), new ObjectMapper());

        TitleGeneratorResponse response = service.generate(request(), true);

        assertThat(response.getTitles()).hasSizeGreaterThanOrEqualTo(5);
    }

    @Test
    void malformedJsonFallsBackToTemplates() {
        TitleGeneratorService service = new TitleGeneratorService(
            new TextSanitizer(), Optional.of(fakeProvider("not json at all")), new ObjectMapper());

        TitleGeneratorResponse response = service.generate(request(), true);

        assertThat(response.getTitles()).hasSizeGreaterThanOrEqualTo(5);
    }

    @Test
    void bannedClaimTitleIsFilteredOutIndividuallyWithoutDiscardingTheRest() {
        String json = """
            {"titles": ["This Is The Best Ever Valorant Video", "Second Title", \
            "Third Title", "Fourth Title", "Fifth Title"], \
            "shortFormTitles": ["Short One", "Short Two", "Short Three"]}""";
        TitleGeneratorService service = new TitleGeneratorService(
            new TextSanitizer(), Optional.of(fakeProvider(json)), new ObjectMapper());

        TitleGeneratorResponse response = service.generate(request(), true);

        // The one banned title is dropped, but 4 clean titles remain -- above the
        // 3-title minimum -- so the caller still gets real AI output, not a wholesale
        // fallback to templates just because one of several titles was bad.
        assertThat(response.getTitles()).noneMatch(t -> t.toUpperCase().contains("BEST EVER"));
        assertThat(response.getTitles()).contains("Second Title");
    }

    @Test
    void allTitlesBannedFallsBackToTemplates() {
        String json = """
            {"titles": ["World Record Valorant Run", "The Greatest Of All Time Play"], \
            "shortFormTitles": ["Short One"]}""";
        TitleGeneratorService service = new TitleGeneratorService(
            new TextSanitizer(), Optional.of(fakeProvider(json)), new ObjectMapper());

        TitleGeneratorResponse response = service.generate(request(), true);

        // Both AI titles were banned claims (0 survive, below the 3 minimum) -- the
        // whole AI result is rejected and templates take over instead.
        assertThat(response.getTitles()).hasSizeGreaterThanOrEqualTo(5);
        assertThat(response.getTitles()).noneMatch(t -> t.toUpperCase().contains("WORLD RECORD"));
    }

    @Test
    void codeFencedJsonIsStripped() {
        String json = "```json\n{\"titles\": [\"Title One\", \"Title Two\", \"Title Three\"], "
            + "\"shortFormTitles\": [\"Short One\"]}\n```";
        TitleGeneratorService service = new TitleGeneratorService(
            new TextSanitizer(), Optional.of(fakeProvider(json)), new ObjectMapper());

        TitleGeneratorResponse response = service.generate(request(), true);

        assertThat(response.getTitles()).contains("Title One");
    }

    @Test
    void noProviderConfiguredFallsBackToTemplatesEvenWithUseAiTrue() {
        TitleGeneratorService service = new TitleGeneratorService(
            new TextSanitizer(), Optional.empty(), new ObjectMapper());

        TitleGeneratorResponse response = service.generate(request(), true);

        assertThat(response.getTitles()).hasSizeGreaterThanOrEqualTo(5);
    }
}
