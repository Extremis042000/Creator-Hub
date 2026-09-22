package com.extremis.hub.ai;

import com.extremis.hub.domain.AiGenerationLog;
import com.extremis.hub.repository.AiGenerationLogRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
}
