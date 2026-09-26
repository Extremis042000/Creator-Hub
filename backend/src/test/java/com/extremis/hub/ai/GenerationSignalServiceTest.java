package com.extremis.hub.ai;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.extremis.hub.domain.OutcomeSignal;
import com.extremis.hub.domain.ToolType;
import com.extremis.hub.repository.AiGenerationLogRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Phase 37: the three ways an outcome signal can land on an AiGenerationLog row. */
@ExtendWith(MockitoExtension.class)
class GenerationSignalServiceTest {

    @Mock private AiGenerationLogRepository logRepository;

    private RefineSessionStore refineSessionStore;
    private GenerationSignalService service;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        refineSessionStore = new RefineSessionStore();
        service = new GenerationSignalService(refineSessionStore, logRepository);
    }

    @Test
    void recordExplicitSignalUpdatesTheSessionsGenerationLogRow() {
        UUID logId = UUID.randomUUID();
        RefineSession session = refineSessionStore.create(userId, ToolType.TITLE_GENERATOR, "sys", "initial", logId);

        service.recordExplicitSignal(session.getId(), userId, ToolType.TITLE_GENERATOR, OutcomeSignal.COPIED);

        verify(logRepository).recordOutcomeSignal(logId, OutcomeSignal.COPIED);
    }

    @Test
    void recordExplicitSignalIsANoOpForAnUnknownSession() {
        service.recordExplicitSignal(UUID.randomUUID(), userId, ToolType.TITLE_GENERATOR, OutcomeSignal.COPIED);

        verify(logRepository, never()).recordOutcomeSignal(org.mockito.ArgumentMatchers.any(), eq(OutcomeSignal.COPIED));
    }

    @Test
    void recordExplicitSignalIsANoOpWhenTheSessionHasNoGenerationLogId() {
        RefineSession session = refineSessionStore.create(userId, ToolType.TITLE_GENERATOR, "sys", "initial", null);

        service.recordExplicitSignal(session.getId(), userId, ToolType.TITLE_GENERATOR, OutcomeSignal.COPIED);

        verify(logRepository, never()).recordOutcomeSignal(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void recordExplicitSignalIsANoOpForTheWrongOwner() {
        UUID logId = UUID.randomUUID();
        RefineSession session = refineSessionStore.create(userId, ToolType.TITLE_GENERATOR, "sys", "initial", logId);

        service.recordExplicitSignal(session.getId(), UUID.randomUUID(), ToolType.TITLE_GENERATOR, OutcomeSignal.COPIED);

        verify(logRepository, never()).recordOutcomeSignal(eq(logId), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void recordRefinedMarksTheSessionsGenerationLogRowIfAbsent() {
        UUID logId = UUID.randomUUID();
        RefineSession session = refineSessionStore.create(userId, ToolType.TITLE_GENERATOR, "sys", "initial", logId);

        service.recordRefined(session);

        verify(logRepository).recordOutcomeSignalIfAbsent(logId, OutcomeSignal.REFINED);
    }

    @Test
    void recordRefinedIsANoOpWhenTheSessionHasNoGenerationLogId() {
        RefineSession session = refineSessionStore.create(userId, ToolType.TITLE_GENERATOR, "sys", "initial", null);

        service.recordRefined(session);

        verify(logRepository, never()).recordOutcomeSignalIfAbsent(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void recordRegeneratedIfApplicableMarksThePriorLiveSessionsLogRow() {
        UUID priorLogId = UUID.randomUUID();
        refineSessionStore.create(userId, ToolType.TITLE_GENERATOR, "sys", "first result", priorLogId);

        service.recordRegeneratedIfApplicable(userId, ToolType.TITLE_GENERATOR);

        verify(logRepository).recordOutcomeSignalIfAbsent(priorLogId, OutcomeSignal.REGENERATED);
    }

    @Test
    void recordRegeneratedIfApplicableIsANoOpWithNoPriorLiveSession() {
        service.recordRegeneratedIfApplicable(userId, ToolType.TITLE_GENERATOR);

        verify(logRepository, never()).recordOutcomeSignalIfAbsent(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void recordRegeneratedIfApplicableIgnoresADifferentToolTypesSession() {
        UUID priorLogId = UUID.randomUUID();
        refineSessionStore.create(userId, ToolType.DESCRIPTION_GENERATOR, "sys", "first result", priorLogId);

        service.recordRegeneratedIfApplicable(userId, ToolType.TITLE_GENERATOR);

        verify(logRepository, never()).recordOutcomeSignalIfAbsent(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
