package com.extremis.hub.ai;

import com.extremis.hub.domain.AiGenerationLog;
import com.extremis.hub.domain.ToolType;
import com.extremis.hub.repository.AiGenerationLogRepository;
import com.extremis.hub.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Phase 28: the AI path's cost/abuse circuit breaker + usage log. A
 * single Render instance, so plain in-memory counters are enough --
 * no need for a distributed rate limiter. Every attempt (success,
 * failure, or throttled) is persisted as one AiGenerationLog row for
 * real cost visibility (see AiUsageService) -- never the prompt or
 * response text, only metadata.
 *
 * Two independent guards, either one can deny a call: a per-user
 * rolling-60s rate limit (defends against one entitled account
 * hammering the AI path) and a global daily call ceiling (this
 * product's equivalent of Triage Desk's --max-budget-usd -- see
 * AiUsageProperties for why it's calls, not dollars).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiUsageGuard {

    public static final String RATE_LIMITED = "RATE_LIMITED";
    public static final String DAILY_CEILING_REACHED = "DAILY_CEILING_REACHED";

    private static final Duration RATE_WINDOW = Duration.ofMinutes(1);

    private final AiUsageProperties properties;
    private final AiGenerationLogRepository logRepository;
    private final UserRepository userRepository;

    private final ConcurrentHashMap<UUID, Deque<Instant>> callTimestamps = new ConcurrentHashMap<>();
    private final AtomicInteger dailyCallCount = new AtomicInteger();
    private volatile LocalDate dailyCounterDate = LocalDate.now(ZoneOffset.UTC);

    /**
     * Reserves one slot against both guards if allowed. Returns null
     * when the call may proceed to the real AI provider; otherwise a
     * denial reason (RATE_LIMITED or DAILY_CEILING_REACHED) the caller
     * should pass to recordThrottled -- the call must NOT reach the
     * provider in that case.
     */
    public String tryAcquire(UUID userId) {
        rolloverDailyCounterIfNeeded();
        if (dailyCallCount.get() >= properties.getDailyCallCeiling()) {
            return DAILY_CEILING_REACHED;
        }
        Deque<Instant> timestamps = callTimestamps.computeIfAbsent(userId, id -> new ArrayDeque<>());
        synchronized (timestamps) {
            Instant cutoff = Instant.now().minus(RATE_WINDOW);
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(cutoff)) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= properties.getRateLimitPerMinute()) {
                return RATE_LIMITED;
            }
            timestamps.addLast(Instant.now());
        }
        dailyCallCount.incrementAndGet();
        return null;
    }

    public void recordSuccess(UUID userId, ToolType toolType, String providerName, String modelName,
            String servedBy, long inputTokens, long outputTokens, long latencyMs) {
        save(userId, toolType, providerName, modelName, servedBy, inputTokens, outputTokens, latencyMs, true, null);
    }

    public void recordFailure(UUID userId, ToolType toolType, String providerName, String failureReason,
            long latencyMs) {
        save(userId, toolType, providerName, null, null, 0, 0, latencyMs, false, failureReason);
    }

    public void recordThrottled(UUID userId, ToolType toolType, String providerName, String reason) {
        save(userId, toolType, providerName, null, null, 0, 0, 0, false, reason);
    }

    private void save(UUID userId, ToolType toolType, String providerName, String modelName, String servedBy,
            long inputTokens, long outputTokens, long latencyMs, boolean success, String failureReason) {
        userRepository.findById(userId).ifPresentOrElse(user -> {
            AiGenerationLog entry = new AiGenerationLog();
            entry.setUser(user);
            entry.setToolType(toolType);
            entry.setProviderName(providerName);
            entry.setModelName(modelName);
            entry.setServedBy(servedBy);
            entry.setInputTokens(inputTokens);
            entry.setOutputTokens(outputTokens);
            entry.setLatencyMs(latencyMs);
            entry.setSuccess(success);
            entry.setFailureReason(failureReason);
            logRepository.save(entry);
        }, () -> log.warn("AI usage log skipped -- user {} not found", userId));
    }

    private void rolloverDailyCounterIfNeeded() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        if (!today.equals(dailyCounterDate)) {
            synchronized (this) {
                if (!today.equals(dailyCounterDate)) {
                    dailyCounterDate = today;
                    dailyCallCount.set(0);
                }
            }
        }
    }
}
