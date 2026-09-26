package com.extremis.hub.domain;

/**
 * Phase 37: a lightweight, first-party proxy for "was this generation
 * good" -- recorded on {@code AiGenerationLog.outcomeSignal}. All three
 * are recorded automatically except COPIED, which needs one explicit
 * signal from the frontend (see GenerationSignalService); REFINED and
 * REGENERATED are inferred server-side from what the user did next,
 * with no new user-facing UI.
 */
public enum OutcomeSignal {
    /** The user copied this result to their clipboard -- a real, explicit "I used this" signal. */
    COPIED,
    /** The user asked to refine this result (Phase 29) -- "not quite right as-is." */
    REFINED,
    /** The user generated again for the same tool shortly after, without copying or refining the prior result -- "didn't want this one." */
    REGENERATED
}
