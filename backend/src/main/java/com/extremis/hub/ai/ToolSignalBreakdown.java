package com.extremis.hub.ai;

/**
 * Phase 38: one tool's all-time outcome-signal breakdown (Phase 37's
 * `outcome_signal` column) -- how many of its successful AI generations
 * were copied, refined further, or immediately regenerated, versus how
 * many carry no signal at all (most of them, most of the time -- not
 * every generation gets acted on).
 */
public record ToolSignalBreakdown(
    long successfulGenerations,
    long copied,
    long refined,
    long regenerated,
    long noSignalYet) {
}
