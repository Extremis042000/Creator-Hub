package com.extremis.hub.tools.title;

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

/** Phase 27: the AI-enhanced path and its template fallback -- the deterministic-only path stays covered by TitleGeneratorServiceTest. Phase 29's refine() flow is covered here too, since it shares this service. */
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
        {"titles": ["My Great Valorant Clutch", "Another Good Title", \
        "Third Title Here", "Fourth One Too", "Fifth One As Well"], \
        "shortFormTitles": ["Short One", "Short Two", "Short Three"]}""";

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
    void useAiTrueWithValidJsonReturnsAiGeneratedTitlesWithARefineSessionId() {
        TitleGeneratorService service = new TitleGeneratorService(
            new TextSanitizer(), Optional.of(fakeProvider(VALID_JSON)), new ObjectMapper(),
            permissiveUsageGuard(), new RefineSessionStore(), newSignalService());

        TitleGeneratorResponse response = service.generate(request(), true, USER_ID);

        assertThat(response.getTitles()).contains("My Great Valorant Clutch");
        assertThat(response.getShortFormTitles()).contains("Short One");
        assertThat(response.getRefineSessionId()).isNotBlank();
    }

    @Test
    void useAiFalseNeverCallsProviderAndUsesTemplatesWithNoRefineSessionId() {
        TitleGeneratorService service = new TitleGeneratorService(
            new TextSanitizer(), Optional.of(failingProvider()), new ObjectMapper(),
            permissiveUsageGuard(), new RefineSessionStore(), newSignalService());

        TitleGeneratorResponse response = service.generate(request(), false, USER_ID);

        assertThat(response.getTitles()).hasSizeGreaterThanOrEqualTo(5);
        assertThat(response.getRefineSessionId()).isNull();
    }

    @Test
    void providerFailureFallsBackToTemplates() {
        TitleGeneratorService service = new TitleGeneratorService(
            new TextSanitizer(), Optional.of(failingProvider()), new ObjectMapper(),
            permissiveUsageGuard(), new RefineSessionStore(), newSignalService());

        TitleGeneratorResponse response = service.generate(request(), true, USER_ID);

        assertThat(response.getTitles()).hasSizeGreaterThanOrEqualTo(5);
    }

    @Test
    void malformedJsonFallsBackToTemplates() {
        TitleGeneratorService service = new TitleGeneratorService(
            new TextSanitizer(), Optional.of(fakeProvider("not json at all")), new ObjectMapper(),
            permissiveUsageGuard(), new RefineSessionStore(), newSignalService());

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
            new TextSanitizer(), Optional.of(fakeProvider(json)), new ObjectMapper(),
            permissiveUsageGuard(), new RefineSessionStore(), newSignalService());

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
            new TextSanitizer(), Optional.of(fakeProvider(json)), new ObjectMapper(),
            permissiveUsageGuard(), new RefineSessionStore(), newSignalService());

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
            new TextSanitizer(), Optional.of(fakeProvider(json)), new ObjectMapper(),
            permissiveUsageGuard(), new RefineSessionStore(), newSignalService());

        TitleGeneratorResponse response = service.generate(request(), true, USER_ID);

        assertThat(response.getTitles()).contains("Title One");
    }

    @Test
    void noProviderConfiguredFallsBackToTemplatesEvenWithUseAiTrue() {
        TitleGeneratorService service = new TitleGeneratorService(
            new TextSanitizer(), Optional.empty(), new ObjectMapper(),
            permissiveUsageGuard(), new RefineSessionStore(), newSignalService());

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
            new TextSanitizer(), Optional.of(countingProvider), new ObjectMapper(), guard, new RefineSessionStore(), newSignalService());

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
            new TextSanitizer(), Optional.of(countingProvider), new ObjectMapper(), guard, new RefineSessionStore(), newSignalService());

        TitleGeneratorResponse response = service.generate(request(), true, USER_ID);

        assertThat(callCount.get()).isZero();
        assertThat(response.getTitles()).hasSizeGreaterThanOrEqualTo(5);
    }

    // ---- Phase 29: refine() ----

    @Test
    void refineOnAValidSessionReturnsRevisedTitlesAndKeepsTheSameSessionId() {
        String refined = """
            {"titles": ["Refined Title One", "Refined Title Two", "Refined Title Three"], \
            "shortFormTitles": ["Refined Short One"]}""";
        RefineSessionStore store = new RefineSessionStore();
        TitleGeneratorService generator = new TitleGeneratorService(
            new TextSanitizer(), Optional.of(fakeProvider(VALID_JSON)), new ObjectMapper(),
            permissiveUsageGuard(), store, newSignalService());
        TitleGeneratorResponse first = generator.generate(request(), true, USER_ID);

        TitleGeneratorService refiner = new TitleGeneratorService(
            new TextSanitizer(), Optional.of(fakeProvider(refined)), new ObjectMapper(),
            permissiveUsageGuard(), store, newSignalService());
        TitleGeneratorResponse refinedResponse = refiner.refine(first.getRefineSessionId(), "make it punchier", USER_ID);

        assertThat(refinedResponse.getTitles()).containsExactly(
            "Refined Title One", "Refined Title Two", "Refined Title Three");
        assertThat(refinedResponse.getRefineSessionId()).isEqualTo(first.getRefineSessionId());
    }

    @Test
    void refineWithUnknownSessionIdThrows() {
        TitleGeneratorService service = new TitleGeneratorService(
            new TextSanitizer(), Optional.of(fakeProvider(VALID_JSON)), new ObjectMapper(),
            permissiveUsageGuard(), new RefineSessionStore(), newSignalService());

        Throwable thrown = catchThrowable(() -> service.refine(UUID.randomUUID().toString(), "shorter", USER_ID));

        assertThat(thrown).isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void refineWithMalformedSessionIdThrowsRatherThanCrashing() {
        TitleGeneratorService service = new TitleGeneratorService(
            new TextSanitizer(), Optional.of(fakeProvider(VALID_JSON)), new ObjectMapper(),
            permissiveUsageGuard(), new RefineSessionStore(), newSignalService());

        Throwable thrown = catchThrowable(() -> service.refine("not-a-uuid", "shorter", USER_ID));

        assertThat(thrown).isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void refineByADifferentUserThanCreatedTheSessionThrows() {
        RefineSessionStore store = new RefineSessionStore();
        TitleGeneratorService owner = new TitleGeneratorService(
            new TextSanitizer(), Optional.of(fakeProvider(VALID_JSON)), new ObjectMapper(),
            permissiveUsageGuard(), store, newSignalService());
        TitleGeneratorResponse first = owner.generate(request(), true, USER_ID);

        TitleGeneratorService attacker = new TitleGeneratorService(
            new TextSanitizer(), Optional.of(fakeProvider(VALID_JSON)), new ObjectMapper(),
            permissiveUsageGuard(), store, newSignalService());
        Throwable thrown = catchThrowable(
            () -> attacker.refine(first.getRefineSessionId(), "give me the good version", UUID.randomUUID()));

        assertThat(thrown).isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void refineFailureThrowsRatherThanFallingBackToTemplates() {
        RefineSessionStore store = new RefineSessionStore();
        TitleGeneratorService generator = new TitleGeneratorService(
            new TextSanitizer(), Optional.of(fakeProvider(VALID_JSON)), new ObjectMapper(),
            permissiveUsageGuard(), store, newSignalService());
        TitleGeneratorResponse first = generator.generate(request(), true, USER_ID);

        TitleGeneratorService refiner = new TitleGeneratorService(
            new TextSanitizer(), Optional.of(failingProvider()), new ObjectMapper(),
            permissiveUsageGuard(), store, newSignalService());
        Throwable thrown = catchThrowable(() -> refiner.refine(first.getRefineSessionId(), "shorter", USER_ID));

        assertThat(thrown).isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void refineBeyondTheTurnCapThrows() {
        RefineSessionStore store = new RefineSessionStore();
        TitleGeneratorService service = new TitleGeneratorService(
            new TextSanitizer(), Optional.of(fakeProvider(VALID_JSON)), new ObjectMapper(),
            permissiveUsageGuard(), store, newSignalService());
        TitleGeneratorResponse first = service.generate(request(), true, USER_ID);
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
        TitleGeneratorService generator = new TitleGeneratorService(
            new TextSanitizer(), Optional.of(fakeProvider(VALID_JSON)), new ObjectMapper(),
            permissiveUsageGuard(), store, newSignalService());
        TitleGeneratorResponse first = generator.generate(request(), true, USER_ID);

        TitleGeneratorService refiner = new TitleGeneratorService(
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
        TitleGeneratorService service = new TitleGeneratorService(
            new TextSanitizer(), Optional.of(fakeProvider(VALID_JSON)), new ObjectMapper(), guard, store, signalService);

        TitleGeneratorResponse first = service.generate(request(), true, USER_ID);
        service.refine(first.getRefineSessionId(), "make it punchier", USER_ID);

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
        TitleGeneratorService service = new TitleGeneratorService(
            new TextSanitizer(), Optional.of(fakeProvider(VALID_JSON)), new ObjectMapper(), guard, store, signalService);

        service.generate(request(), true, USER_ID); // first generation, abandoned unrefined/uncopied below
        service.generate(request(), true, USER_ID); // regenerating -- should mark the first one REGENERATED

        verify(logRepository).recordOutcomeSignalIfAbsent(
            org.mockito.ArgumentMatchers.any(), eq(com.extremis.hub.domain.OutcomeSignal.REGENERATED));
    }
}
