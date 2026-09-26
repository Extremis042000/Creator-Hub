package com.extremis.hub.ai;

import com.extremis.hub.domain.AiGenerationLog;
import com.extremis.hub.domain.OutcomeSignal;
import com.extremis.hub.repository.AiGenerationLogRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Aggregates today's (UTC) ai_generation_log rows for the admin "AI usage today" view. */
@Service
@RequiredArgsConstructor
public class AiUsageService {

    private final AiGenerationLogRepository logRepository;
    private final AiUsageProperties properties;

    public AiUsageSummaryResponse getTodaySummary() {
        Instant startOfDayUtc = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<AiGenerationLog> rows = logRepository.findByCreatedAtGreaterThanEqual(startOfDayUtc);

        long throttled = rows.stream().filter(r -> !r.isSuccess() && isThrottleReason(r.getFailureReason())).count();
        long success = rows.stream().filter(AiGenerationLog::isSuccess).count();
        long failure = rows.size() - success - throttled;

        long totalInputTokens = rows.stream().mapToLong(AiGenerationLog::getInputTokens).sum();
        long totalOutputTokens = rows.stream().mapToLong(AiGenerationLog::getOutputTokens).sum();
        double averageLatencyMs = rows.stream()
            .filter(AiGenerationLog::isSuccess)
            .mapToLong(AiGenerationLog::getLatencyMs)
            .average()
            .orElse(0);

        Map<String, Long> callsByProvider = rows.stream()
            .collect(Collectors.groupingBy(AiGenerationLog::getProviderName, Collectors.counting()));

        return new AiUsageSummaryResponse(rows.size(), success, failure, throttled,
            totalInputTokens, totalOutputTokens, averageLatencyMs, callsByProvider, properties.getDailyCallCeiling());
    }

    private boolean isThrottleReason(String reason) {
        return AiUsageGuard.RATE_LIMITED.equals(reason) || AiUsageGuard.DAILY_CEILING_REACHED.equals(reason);
    }

    /**
     * Phase 38: all-time (not scoped to today, unlike getTodaySummary) --
     * signals accumulate slowly enough that a "today" view would look
     * empty most days and never show the trend the admin panel wants.
     */
    public AiSignalSummaryResponse getSignalSummary() {
        List<AiGenerationLog> successfulRows = logRepository.findBySuccessTrue();

        Map<String, ToolSignalBreakdown> byTool = new HashMap<>();
        Map<String, List<AiGenerationLog>> grouped = successfulRows.stream()
            .collect(Collectors.groupingBy(r -> r.getToolType().name()));

        for (Map.Entry<String, List<AiGenerationLog>> entry : grouped.entrySet()) {
            List<AiGenerationLog> rows = entry.getValue();
            long copied = countSignal(rows, OutcomeSignal.COPIED);
            long refined = countSignal(rows, OutcomeSignal.REFINED);
            long regenerated = countSignal(rows, OutcomeSignal.REGENERATED);
            long noSignalYet = rows.size() - copied - refined - regenerated;
            byTool.put(entry.getKey(), new ToolSignalBreakdown(rows.size(), copied, refined, regenerated, noSignalYet));
        }
        return new AiSignalSummaryResponse(byTool);
    }

    private long countSignal(List<AiGenerationLog> rows, OutcomeSignal signal) {
        return rows.stream().filter(r -> r.getOutcomeSignal() == signal).count();
    }
}
