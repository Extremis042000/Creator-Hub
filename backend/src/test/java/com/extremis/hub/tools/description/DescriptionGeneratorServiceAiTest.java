package com.extremis.hub.tools.description;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.extremis.hub.ai.AiGenerationException;
import com.extremis.hub.ai.AiGenerationProvider;
import com.extremis.hub.ai.AiGenerationRequest;
import com.extremis.hub.ai.AiGenerationResult;
import com.extremis.hub.ai.AiUsageGuard;
import com.extremis.hub.ai.AiUsageProperties;
import com.extremis.hub.ai.GenerationSignalService;
import com.extremis.hub.ai.RefineSessionStore;
import com.extremis.hub.repository.AiGenerationLogRepository;
import com.extremis.hub.repository.UserRepository;
import com.extremis.hub.tools.common.TextSanitizer;
import com.extremis.hub.web.BusinessRuleViolationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Phase 27: the AI-enhanced path and its template fallback -- the deterministic-only path stays covered by DescriptionGeneratorServiceTest. Phase 29's refine() flow is covered here too, since it shares this service. */
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

    /** Generous limits (some of these tests make several calls, e.g. exercising Phase 29's turn cap) + mocked repos -- Phase 28's guard itself isn't under test here (see AiUsageGuardTest). */
    private AiUsageGuard permissiveUsageGuard() {
        AiUsageProperties generous = new AiUsageProperties();
        generous.setRateLimitPerMinute(1000);
        generous.setDailyCallCeiling(1000);
        return new AiUsageGuard(generous, mock(AiGenerationLogRepository.class), mock(UserRepository.class));
    }

    /**
     * Phase 37: not under test here (see GenerationSignalServiceTest) --
     * wired to its own fresh, unrelated RefineSessionStore/mocked
     * repository so every call inside generate()/refine() silently
     * no-ops without affecting any assertion in this file.
     */
    private GenerationSignalService newSignalService() {
        return new GenerationSignalService(new RefineSessionStore(), mock(AiGenerationLogRepository.class));
    }

    private static final String VALID_JSON = """
        {"description": "A great BGMI solo vs squad chicken dinner video.", \
        "seoKeywordsSection": "bgmi, chicken dinner, solo vs squad", \
        "hashtags": ["#BGMI", "#ChickenDinner"]}""";

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
    void useAiTrueWithValidJsonReturnsAiGeneratedDescriptionWithARefineSessionId() {
        DescriptionGeneratorService service = new DescriptionGeneratorService(
            new TextSanitizer(), Optional.of(fakeProvider(VALID_JSON)), new ObjectMapper(),
            permissiveUsageGuard(), new RefineSessionStore(), newSignalService());

        DescriptionGeneratorResponse response = service.generate(request(), true, USER_ID);

        assertThat(response.getDescription()).isEqualTo("A great BGMI solo vs squad chicken dinner video.");
        assertThat(response.getHashtags()).containsExactly("#BGMI", "#ChickenDinner");
        assertThat(response.getRefineSessionId()).isNotBlank();
    }

    @Test
    void useAiFalseNeverCallsProviderAndUsesTemplatesWithNoRefineSessionId() {
        DescriptionGeneratorService service = new DescriptionGeneratorService(
            new TextSanitizer(), Optional.of(failingProvider()), new ObjectMapper(),
            permissiveUsageGuard(), new RefineSessionStore(), newSignalService());

        DescriptionGeneratorResponse response = service.generate(request(), false, USER_ID);

        assertThat(response.getDescription()).contains("EXTREMIS Plays");
        assertThat(response.getRefineSessionId()).isNull();
    }

    @Test
    void providerFailureFallsBackToTemplates() {
        DescriptionGeneratorService service = new DescriptionGeneratorService(
            new TextSanitizer(), Optional.of(failingProvider()), new ObjectMapper(),
            permissiveUsageGuard(), new RefineSessionStore(), newSignalService());

        DescriptionGeneratorResponse response = service.generate(request(), true, USER_ID);

        assertThat(response.getDescription()).contains("EXTREMIS Plays");
    }

    @Test
    void malformedJsonFallsBackToTemplates() {
        DescriptionGeneratorService service = new DescriptionGeneratorService(
            new TextSanitizer(), Optional.of(fakeProvider("not json at all")), new ObjectMapper(),
            permissiveUsageGuard(), new RefineSessionStore(), newSignalService());

        DescriptionGeneratorResponse response = service.generate(request(), true, USER_ID);

        assertThat(response.getDescription()).contains("EXTREMIS Plays");
    }

    @Test
    void bannedClaimInDescriptionFallsBackToTemplates() {
        String json = """
            {"description": "This is the best ever BGMI video, a world record run.", \
            "seoKeywordsSection": "bgmi", "hashtags": ["#BGMI"]}""";
        DescriptionGeneratorService service = new DescriptionGeneratorService(
            new TextSanitizer(), Optional.of(fakeProvider(json)), new ObjectMapper(),
            permissiveUsageGuard(), new RefineSessionStore(), newSignalService());

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
            new TextSanitizer(), Optional.of(fakeProvider(json)), new ObjectMapper(),
            permissiveUsageGuard(), new RefineSessionStore(), newSignalService());

        DescriptionGeneratorResponse response = service.generate(request(), true, USER_ID);

        assertThat(response.getHashtags()).isNotEmpty();
        assertThat(response.getDescription()).contains("EXTREMIS Plays");
    }

    @Test
    void noProviderConfiguredFallsBackToTemplatesEvenWithUseAiTrue() {
        DescriptionGeneratorService service = new DescriptionGeneratorService(
            new TextSanitizer(), Optional.empty(), new ObjectMapper(),
            permissiveUsageGuard(), new RefineSessionStore(), newSignalService());

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
            new TextSanitizer(), Optional.of(countingProvider), new ObjectMapper(), guard, new RefineSessionStore(), newSignalService());

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
            new TextSanitizer(), Optional.of(countingProvider), new ObjectMapper(), guard, new RefineSessionStore(), newSignalService());

        DescriptionGeneratorResponse response = service.generate(request(), true, USER_ID);

        assertThat(callCount.get()).isZero();
        assertThat(response.getDescription()).contains("EXTREMIS Plays");
    }

    // ---- Phase 29: refine() ----

    @Test
    void refineOnAValidSessionReturnsRevisedDescriptionAndKeepsTheSameSessionId() {
        String refined = """
            {"description": "A shorter, punchier BGMI description.", \
            "seoKeywordsSection": "bgmi", "hashtags": ["#BGMI"]}""";
        RefineSessionStore store = new RefineSessionStore();
        DescriptionGeneratorService generator = new DescriptionGeneratorService(
            new TextSanitizer(), Optional.of(fakeProvider(VALID_JSON)), new ObjectMapper(),
            permissiveUsageGuard(), store, newSignalService());
        DescriptionGeneratorResponse first = generator.generate(request(), true, USER_ID);

        DescriptionGeneratorService refiner = new DescriptionGeneratorService(
            new TextSanitizer(), Optional.of(fakeProvider(refined)), new ObjectMapper(),
            permissiveUsageGuard(), store, newSignalService());
        DescriptionGeneratorResponse refinedResponse =
            refiner.refine(first.getRefineSessionId(), "make it shorter", USER_ID);

        assertThat(refinedResponse.getDescription()).isEqualTo("A shorter, punchier BGMI description.");
        assertThat(refinedResponse.getRefineSessionId()).isEqualTo(first.getRefineSessionId());
    }

    @Test
    void refineWithUnknownSessionIdThrows() {
        DescriptionGeneratorService service = new DescriptionGeneratorService(
            new TextSanitizer(), Optional.of(fakeProvider(VALID_JSON)), new ObjectMapper(),
            permissiveUsageGuard(), new RefineSessionStore(), newSignalService());

        Throwable thrown = catchThrowable(() -> service.refine(UUID.randomUUID().toString(), "shorter", USER_ID));

        assertThat(thrown).isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void refineWithMalformedSessionIdThrowsRatherThanCrashing() {
        DescriptionGeneratorService service = new DescriptionGeneratorService(
            new TextSanitizer(), Optional.of(fakeProvider(VALID_JSON)), new ObjectMapper(),
            permissiveUsageGuard(), new RefineSessionStore(), newSignalService());

        Throwable thrown = catchThrowable(() -> service.refine("not-a-uuid", "shorter", USER_ID));

        assertThat(thrown).isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void refineByADifferentUserThanCreatedTheSessionThrows() {
        RefineSessionStore store = new RefineSessionStore();
        DescriptionGeneratorService owner = new DescriptionGeneratorService(
            new TextSanitizer(), Optional.of(fakeProvider(VALID_JSON)), new ObjectMapper(),
            permissiveUsageGuard(), store, newSignalService());
        DescriptionGeneratorResponse first = owner.generate(request(), true, USER_ID);

        DescriptionGeneratorService attacker = new DescriptionGeneratorService(
            new TextSanitizer(), Optional.of(fakeProvider(VALID_JSON)), new ObjectMapper(),
            permissiveUsageGuard(), store, newSignalService());
        Throwable thrown = catchThrowable(
            () -> attacker.refine(first.getRefineSessionId(), "give me the good version", UUID.randomUUID()));

        assertThat(thrown).isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void refineFailureThrowsRatherThanFallingBackToTemplates() {
        RefineSessionStore store = new RefineSessionStore();
        DescriptionGeneratorService generator = new DescriptionGeneratorService(
            new TextSanitizer(), Optional.of(fakeProvider(VALID_JSON)), new ObjectMapper(),
            permissiveUsageGuard(), store, newSignalService());
        DescriptionGeneratorResponse first = generator.generate(request(), true, USER_ID);

        DescriptionGeneratorService refiner = new DescriptionGeneratorService(
            new TextSanitizer(), Optional.of(failingProvider()), new ObjectMapper(),
            permissiveUsageGuard(), store, newSignalService());
        Throwable thrown = catchThrowable(() -> refiner.refine(first.getRefineSessionId(), "shorter", USER_ID));

        assertThat(thrown).isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void refineBeyondTheTurnCapThrows() {
        RefineSessionStore store = new RefineSessionStore();
        DescriptionGeneratorService service = new DescriptionGeneratorService(
            new TextSanitizer(), Optional.of(fakeProvider(VALID_JSON)), new ObjectMapper(),
            permissiveUsageGuard(), store, newSignalService());
        DescriptionGeneratorResponse first = service.generate(request(), true, USER_ID);
        String sessionId = first.getRefineSessionId();

        for (int i = 0; i < RefineSessionStore.MAX_REFINE_TURNS; i++) {
            service.refine(sessionId, "iteration " + i, USER_ID);
        }
        Throwable thrown = catchThrowable(() -> service.refine(sessionId, "one more please", USER_ID));

        assertThat(thrown).isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void refineWithNoProviderConfiguredThrows() {
        RefineSessionStore store = new RefineSessionStore();
        DescriptionGeneratorService generator = new DescriptionGeneratorService(
            new TextSanitizer(), Optional.of(fakeProvider(VALID_JSON)), new ObjectMapper(),
            permissiveUsageGuard(), store, newSignalService());
        DescriptionGeneratorResponse first = generator.generate(request(), true, USER_ID);

        DescriptionGeneratorService refiner = new DescriptionGeneratorService(
            new TextSanitizer(), Optional.empty(), new ObjectMapper(), permissiveUsageGuard(), store, newSignalService());
        Throwable thrown = catchThrowable(() -> refiner.refine(first.getRefineSessionId(), "shorter", USER_ID));

        assertThat(thrown).isInstanceOf(BusinessRuleViolationException.class);
    }

    // ---- Phase 37: outcome-signal wiring, end to end through the real service ----

    @Test
    void refiningAResultRecordsAREFINEDSignalOnItsOriginalGenerationLogRow() {
        AiGenerationLogRepository logRepository = mock(AiGenerationLogRepository.class);
        when(logRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> {
            com.extremis.hub.domain.AiGenerationLog entry = inv.getArgument(0);
            entry.setId(UUID.randomUUID());
            return entry;
        });
        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(new com.extremis.hub.domain.User()));
        AiUsageProperties generous = new AiUsageProperties();
        generous.setRateLimitPerMinute(1000);
        generous.setDailyCallCeiling(1000);
        AiUsageGuard guard = new AiUsageGuard(generous, logRepository, userRepository);
        RefineSessionStore store = new RefineSessionStore();
        GenerationSignalService signalService = new GenerationSignalService(store, logRepository);
        DescriptionGeneratorService service = new DescriptionGeneratorService(
            new TextSanitizer(), Optional.of(fakeProvider(VALID_JSON)), new ObjectMapper(), guard, store, signalService);

        DescriptionGeneratorResponse first = service.generate(request(), true, USER_ID);
        service.refine(first.getRefineSessionId(), "make it shorter", USER_ID);

        verify(logRepository).recordOutcomeSignalIfAbsent(
            org.mockito.ArgumentMatchers.any(), eq(com.extremis.hub.domain.OutcomeSignal.REFINED));
    }

    @Test
    void generatingAgainWithoutRefiningOrCopyingRecordsAREGENERATEDSignalOnThePriorResult() {
        AiGenerationLogRepository logRepository = mock(AiGenerationLogRepository.class);
        when(logRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> {
            com.extremis.hub.domain.AiGenerationLog entry = inv.getArgument(0);
            entry.setId(UUID.randomUUID());
            return entry;
        });
        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(new com.extremis.hub.domain.User()));
        AiUsageProperties generous = new AiUsageProperties();
        generous.setRateLimitPerMinute(1000);
        generous.setDailyCallCeiling(1000);
        AiUsageGuard guard = new AiUsageGuard(generous, logRepository, userRepository);
        RefineSessionStore store = new RefineSessionStore();
        GenerationSignalService signalService = new GenerationSignalService(store, logRepository);
        DescriptionGeneratorService service = new DescriptionGeneratorService(
            new TextSanitizer(), Optional.of(fakeProvider(VALID_JSON)), new ObjectMapper(), guard, store, signalService);

        service.generate(request(), true, USER_ID); // first generation, abandoned unrefined/uncopied below
        service.generate(request(), true, USER_ID); // regenerating -- should mark the first one REGENERATED

        verify(logRepository).recordOutcomeSignalIfAbsent(
            org.mockito.ArgumentMatchers.any(), eq(com.extremis.hub.domain.OutcomeSignal.REGENERATED));
    }
}
