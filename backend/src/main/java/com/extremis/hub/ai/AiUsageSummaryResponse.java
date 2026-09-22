package com.extremis.hub.ai;

import java.util.Map;

/** Today (UTC) so far -- see AiUsageService. Not a running total across all time. */
public record AiUsageSummaryResponse(
    long totalCalls,
    long successCount,
    long failureCount,
    long throttledCount,
    long totalInputTokens,
    long totalOutputTokens,
    double averageLatencyMs,
    Map<String, Long> callsByProvider,
    int dailyCallCeiling) {
}
