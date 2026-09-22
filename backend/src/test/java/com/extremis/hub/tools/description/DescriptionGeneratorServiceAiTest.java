package com.extremis.hub.tools.description;

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

/** Phase 27: the AI-enhanced path and its template fallback -- the deterministic-only path stays covered by DescriptionGeneratorServiceTest. */
class DescriptionGeneratorServiceAiTest {

    private static final UUID USER_ID = UUID.randomUUID();

    private DescriptionGeneratorRequest request() {
        DescriptionGeneratorRequest r = new DescriptionGeneratorRequest();
        r.setGame("BGMI");
        r.setTopic("Solo vs Squad chicken dinner");
        r.setChannelName("EXTREMIS Plays");
        r.setKeywords(List.of("bgmi highlights"));
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
    void useAiTrueWithValidJsonReturnsAiGeneratedDescription() {
        String json = """
            {"description": "A great BGMI solo vs squad chicken dinner video.", \
            "seoKeywordsSection": "bgmi, chicken dinner, solo vs squad", \
            "hashtags": ["#BGMI", "#ChickenDinner"]}""";
        DescriptionGeneratorService service = new DescriptionGeneratorService(
            new TextSanitizer(), Optional.of(fakeProvider(json)), new ObjectMapper(), permissiveUsageGuard());

        DescriptionGeneratorResponse response = service.generate(request(), true, USER_ID);

        assertThat(response.getDescription()).isEqualTo("A great BGMI solo vs squad chicken dinner video.");
        assertThat(response.getHashtags()).containsExactly("#BGMI", "#ChickenDinner");
    }

    @Test
    void useAiFalseNeverCallsProviderAndUsesTemplates() {
        DescriptionGeneratorService service = new DescriptionGeneratorService(
            new TextSanitizer(), Optional.of(failingProvider()), new ObjectMapper(), permissiveUsageGuard());

        DescriptionGeneratorResponse response = service.generate(request(), false, USER_ID);

        assertThat(response.getDescription()).contains("EXTREMIS Plays");
    }

    @Test
    void providerFailureFallsBackToTemplates() {
        DescriptionGeneratorService service = new DescriptionGeneratorService(
            new TextSanitizer(), Optional.of(failingProvider()), new ObjectMapper(), permissiveUsageGuard());

        DescriptionGeneratorResponse response = service.generate(request(), true, USER_ID);

        assertThat(response.getDescription()).contains("EXTREMIS Plays");
    }

    @Test
    void malformedJsonFallsBackToTemplates() {
        DescriptionGeneratorService service = new DescriptionGeneratorService(
            new TextSanitizer(), Optional.of(fakeProvider("not json at all")), new ObjectMapper(), permissiveUsageGuard());

        DescriptionGeneratorResponse response = service.generate(request(), true, USER_ID);

        assertThat(response.getDescription()).contains("EXTREMIS Plays");
    }

    @Test
    void bannedClaimInDescriptionFallsBackToTemplates() {
        String json = """
            {"description": "This is the best ever BGMI video, a world record run.", \
            "seoKeywordsSection": "bgmi", "hashtags": ["#BGMI"]}""";
        DescriptionGeneratorService service = new DescriptionGeneratorService(
            new TextSanitizer(), Optional.of(fakeProvider(json)), new ObjectMapper(), permissiveUsageGuard());

        DescriptionGeneratorResponse response = service.generate(request(), true, USER_ID);

        assertThat(response.getDescription()).doesNotContainIgnoringCase("best ever");
        assertThat(response.getDescription()).contains("EXTREMIS Plays");
    }

    @Test
    void noHashtagsFallsBackToTemplates() {
        String json = """
            {"description": "A fine description with no hashtags supplied at all here.", \
            "seoKeywordsSection": "bgmi", "hashtags": []}""";
        DescriptionGeneratorService service = new DescriptionGeneratorService(
            new TextSanitizer(), Optional.of(fakeProvider(json)), new ObjectMapper(), permissiveUsageGuard());

        DescriptionGeneratorResponse response = service.generate(request(), true, USER_ID);

        assertThat(response.getHashtags()).isNotEmpty();
        assertThat(response.getDescription()).contains("EXTREMIS Plays");
    }

    @Test
    void noProviderConfiguredFallsBackToTemplatesEvenWithUseAiTrue() {
        DescriptionGeneratorService service = new DescriptionGeneratorService(
            new TextSanitizer(), Optional.empty(), new ObjectMapper(), permissiveUsageGuard());

        DescriptionGeneratorResponse response = service.generate(request(), true, USER_ID);

        assertThat(response.getDescription()).contains("EXTREMIS Plays");
    }

    @Test
    void rateLimitedCallFallsBackToTemplatesWithoutEverCallingTheProvider() {
        AtomicInteger callCount = new AtomicInteger();
        AiGenerationProvider countingProvider = new AiGenerationProvider() {
            @Override public String getProviderName() { return "fake"; }
            @Override public String getModelName() { return "fake-model"; }
            @Override public AiGenerationResult generate(AiGenerationRequest request) {
                callCount.incrementAndGet();
                return new AiGenerationResult("{\"description\":\"\",\"hashtags\":[]}", 1, 1, "fake-model");
            }
        };
        AiUsageProperties tightLimit = new AiUsageProperties();
        tightLimit.setRateLimitPerMinute(1);
        AiUsageGuard guard = new AiUsageGuard(tightLimit, mock(AiGenerationLogRepository.class), mock(UserRepository.class));
        DescriptionGeneratorService service = new DescriptionGeneratorService(
            new TextSanitizer(), Optional.of(countingProvider), new ObjectMapper(), guard);

        service.generate(request(), true, USER_ID); // consumes the one allowed slot (fails validation -> falls back, but still counted against the rate limit)
        DescriptionGeneratorResponse second = service.generate(request(), true, USER_ID); // should be throttled before ever reaching the provider

        assertThat(callCount.get()).isEqualTo(1);
        assertThat(second.getDescription()).contains("EXTREMIS Plays"); // template fallback
    }

    @Test
    void dailyCeilingReachedFallsBackToTemplatesWithoutEverCallingTheProvider() {
        AtomicInteger callCount = new AtomicInteger();
        AiGenerationProvider countingProvider = new AiGenerationProvider() {
            @Override public String getProviderName() { return "fake"; }
            @Override public String getModelName() { return "fake-model"; }
            @Override public AiGenerationResult generate(AiGenerationRequest request) {
                callCount.incrementAndGet();
                return new AiGenerationResult("{\"description\":\"\",\"hashtags\":[]}", 1, 1, "fake-model");
            }
        };
        AiUsageProperties tightCeiling = new AiUsageProperties();
        tightCeiling.setDailyCallCeiling(0);
        AiUsageGuard guard = new AiUsageGuard(tightCeiling, mock(AiGenerationLogRepository.class), mock(UserRepository.class));
        DescriptionGeneratorService service = new DescriptionGeneratorService(
            new TextSanitizer(), Optional.of(countingProvider), new ObjectMapper(), guard);

        DescriptionGeneratorResponse response = service.generate(request(), true, USER_ID);

        assertThat(callCount.get()).isZero();
        assertThat(response.getDescription()).contains("EXTREMIS Plays");
    }
}
