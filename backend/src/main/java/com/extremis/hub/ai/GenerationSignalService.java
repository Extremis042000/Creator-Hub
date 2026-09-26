package com.extremis.hub.ai;

import com.extremis.hub.domain.OutcomeSignal;
import com.extremis.hub.domain.ToolType;
import com.extremis.hub.repository.AiGenerationLogRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Phase 37: records OutcomeSignal on the AiGenerationLog row a
 * RefineSession points at. Shared by both TitleGeneratorService and
 * DescriptionGeneratorService (and their controllers' /generation-signal
 * endpoints) rather than duplicated -- the logic is identical, only the
 * tool type differs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GenerationSignalService {

    private final RefineSessionStore refineSessionStore;
    private final AiGenerationLogRepository logRepository;

    /**
     * Explicit signal from the frontend (currently only COPIED) --
     * always allowed to overwrite an earlier inferred signal, since a
     * user explicitly copying a result is the strongest positive
     * signal available. Silently a no-op if the session doesn't exist,
     * isn't owned by this user, is for a different tool, or never got
     * a generationLogId in the first place -- this is best-effort
     * instrumentation, never something a caller should have to retry
     * or that should surface as an error to the user.
     */
    public void recordExplicitSignal(UUID sessionId, UUID userId, ToolType toolType, OutcomeSignal signal) {
        refineSessionStore.find(sessionId, userId, toolType).ifPresent(session -> {
            if (session.getGenerationLogId() != null) {
                logRepository.recordOutcomeSignal(session.getGenerationLogId(), signal);
            }
        });
    }

    /**
     * Called from refine() on a successful call -- the user asking to
     * refine is itself the signal ("not quite right as-is"), no
     * frontend involvement needed. First-signal-wins: never overwrites
     * a signal already recorded (e.g. this exact result was already
     * copied before being refined further).
     */
    public void recordRefined(RefineSession session) {
        if (session.getGenerationLogId() != null) {
            logRepository.recordOutcomeSignalIfAbsent(session.getGenerationLogId(), OutcomeSignal.REFINED);
        }
    }

    /**
     * Called right before generate() creates a new session -- if the
     * same user has a still-live, unsignaled session for the same
     * tool, generating again without having copied or refined it is
     * itself the signal ("didn't want that one"). First-signal-wins,
     * same reasoning as recordRefined.
     */
    public void recordRegeneratedIfApplicable(UUID userId, ToolType toolType) {
        refineSessionStore.findLatestLive(userId, toolType).ifPresent(previous -> {
            if (previous.getGenerationLogId() != null) {
                logRepository.recordOutcomeSignalIfAbsent(previous.getGenerationLogId(), OutcomeSignal.REGENERATED);
            }
        });
    }
}
