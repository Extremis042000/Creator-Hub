package com.extremis.hub.tools.title;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.extremis.hub.ai.AiGenerationException;
import com.extremis.hub.ai.AiGenerationProvider;
import com.extremis.hub.ai.AiGenerationRequest;
import com.extremis.hub.ai.AiGenerationResult;
import com.extremis.hub.ai.AiUsageGuard;
import com.extremis.hub.ai.AiUsageProperties;
import com.extremis.hub.repository.AiGenerationLogRepository;
import com.extremis.hub.repository.UserRepository;
import com.extremis.hub.tools.common.TextSanitizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Phase 27: the AI-enhanced path and its template fallback -- the deterministic-only path stays covered by TitleGeneratorServiceTest. */
class TitleGeneratorServiceAiTest {

    private static final UUID USER_ID = UUID.randomUUID();

    private TitleGeneratorRequest request() {
        TitleGeneratorRequest r = new TitleGeneratorRequest();
        r.setGame("Valorant");
        r.setTopic("1v5 clutch on Ascent");
        r.setVideoType(VideoType.HIGHLIGHT);
        r.setTone(Tone.HYPE);
        r.setKeywords(List.of("ace"));
        return r;
    }

    /** Generous limits (these tests make at most 1-2 calls) + mocked repos -- Phase 28's guard, not under test here (see AiUsageGuardTest). */
    private AiUsageGuard permissiveUsageGuard() {
        return new AiUsageGuard(new AiUsageProperties(), mock(AiGenerationLogRepository.class), mock(UserRepository.class));
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
            new TextSanitizer(), Optional.of(fakeProvider(json)), new ObjectMapper(), permissiveUsageGuard());

        TitleGeneratorResponse response = service.generate(request(), true, USER_ID);

        assertThat(response.getTitles()).contains("My Great Valorant Clutch");
        assertThat(response.getShortFormTitles()).contains("Short One");
    }

    @Test
    void useAiFalseNeverCallsProviderAndUsesTemplates() {
        TitleGeneratorService service = new TitleGeneratorService(
            new TextSanitizer(), Optional.of(failingProvider()), new ObjectMapper(), permissiveUsageGuard());

        TitleGeneratorResponse response = service.generate(request(), false, USER_ID);

        assertThat(response.getTitles()).hasSizeGreaterThanOrEqualTo(5);
    }

    @Test
    void providerFailureFallsBackToTemplates() {
        TitleGeneratorService service = new TitleGeneratorService(
            new TextSanitizer(), Optional.of(failingProvider()), new ObjectMapper(), permissiveUsageGuard());

        TitleGeneratorResponse response = service.generate(request(), true, USER_ID);

        assertThat(response.getTitles()).hasSizeGreaterThanOrEqualTo(5);
    }

    @Test
    void malformedJsonFallsBackToTemplates() {
        TitleGeneratorService service = new TitleGeneratorService(
            new TextSanitizer(), Optional.of(fakeProvider("not json at all")), new ObjectMapper(), permissiveUsageGuard());

        TitleGeneratorResponse response = service.generate(request(), true, USER_ID);

        assertThat(response.getTitles()).hasSizeGreaterThanOrEqualTo(5);
    }

    @Test
    void bannedClaimTitleIsFilteredOutIndividuallyWithoutDiscardingTheRest() {
        String json = """
            {"titles": ["This Is The Best Ever Valorant Video", "Second Title", \
            "Third Title", "Fourth Title", "Fifth Title"], \
            "shortFormTitles": ["Short One", "Short Two", "Short Three"]}""";
        TitleGeneratorService service = new TitleGeneratorService(
            new TextSanitizer(), Optional.of(fakeProvider(json)), new ObjectMapper(), permissiveUsageGuard());

        TitleGeneratorResponse response = service.generate(request(), true, USER_ID);

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
            new TextSanitizer(), Optional.of(fakeProvider(json)), new ObjectMapper(), permissiveUsageGuard());

        TitleGeneratorResponse response = service.generate(request(), true, USER_ID);

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
            new TextSanitizer(), Optional.of(fakeProvider(json)), new ObjectMapper(), permissiveUsageGuard());

        TitleGeneratorResponse response = service.generate(request(), true, USER_ID);

        assertThat(response.getTitles()).contains("Title One");
    }

    @Test
    void noProviderConfiguredFallsBackToTemplatesEvenWithUseAiTrue() {
        TitleGeneratorService service = new TitleGeneratorService(
            new TextSanitizer(), Optional.empty(), new ObjectMapper(), permissiveUsageGuard());

        TitleGeneratorResponse response = service.generate(request(), true, USER_ID);

        assertThat(response.getTitles()).hasSizeGreaterThanOrEqualTo(5);
    }

    @Test
    void rateLimitedCallFallsBackToTemplatesWithoutEverCallingTheProvider() {
        AtomicInteger callCount = new AtomicInteger();
        AiGenerationProvider countingProvider = new AiGenerationProvider() {
            @Override public String getProviderName() { return "fake"; }
            @Override public String getModelName() { return "fake-model"; }
            @Override public AiGenerationResult generate(AiGenerationRequest request) {
                callCount.incrementAndGet();
                return new AiGenerationResult("{\"titles\":[],\"shortFormTitles\":[]}", 1, 1, "fake-model");
            }
        };
        AiUsageProperties tightLimit = new AiUsageProperties();
        tightLimit.setRateLimitPerMinute(1);
        AiUsageGuard guard = new AiUsageGuard(tightLimit, mock(AiGenerationLogRepository.class), mock(UserRepository.class));
        TitleGeneratorService service = new TitleGeneratorService(
            new TextSanitizer(), Optional.of(countingProvider), new ObjectMapper(), guard);

        service.generate(request(), true, USER_ID); // consumes the one allowed slot (fails validation -> falls back, but still counted against the rate limit)
        TitleGeneratorResponse second = service.generate(request(), true, USER_ID); // should be throttled before ever reaching the provider

        assertThat(callCount.get()).isEqualTo(1);
        assertThat(second.getTitles()).hasSizeGreaterThanOrEqualTo(5); // template fallback
    }

    @Test
    void dailyCeilingReachedFallsBackToTemplatesWithoutEverCallingTheProvider() {
        AtomicInteger callCount = new AtomicInteger();
        AiGenerationProvider countingProvider = new AiGenerationProvider() {
            @Override public String getProviderName() { return "fake"; }
            @Override public String getModelName() { return "fake-model"; }
            @Override public AiGenerationResult generate(AiGenerationRequest request) {
                callCount.incrementAndGet();
                return new AiGenerationResult("{\"titles\":[],\"shortFormTitles\":[]}", 1, 1, "fake-model");
            }
        };
        AiUsageProperties tightCeiling = new AiUsageProperties();
        tightCeiling.setDailyCallCeiling(0);
        AiUsageGuard guard = new AiUsageGuard(tightCeiling, mock(AiGenerationLogRepository.class), mock(UserRepository.class));
        TitleGeneratorService service = new TitleGeneratorService(
            new TextSanitizer(), Optional.of(countingProvider), new ObjectMapper(), guard);

        TitleGeneratorResponse response = service.generate(request(), true, USER_ID);

        assertThat(callCount.get()).isZero();
        assertThat(response.getTitles()).hasSizeGreaterThanOrEqualTo(5);
    }
}
