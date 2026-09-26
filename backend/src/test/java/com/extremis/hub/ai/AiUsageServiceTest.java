package com.extremis.hub.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.extremis.hub.domain.AiGenerationLog;
import com.extremis.hub.domain.OutcomeSignal;
import com.extremis.hub.domain.ToolType;
import com.extremis.hub.repository.AiGenerationLogRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiUsageServiceTest {

    @Mock private AiGenerationLogRepository logRepository;

    private AiUsageProperties properties;
    private AiUsageService service;

    @BeforeEach
    void setUp() {
        properties = new AiUsageProperties();
        properties.setDailyCallCeiling(200);
        service = new AiUsageService(logRepository, properties);
    }

    private AiGenerationLog row(String provider, boolean success, String failureReason, long inputTokens,
            long outputTokens, long latencyMs) {
        AiGenerationLog entry = new AiGenerationLog();
        entry.setToolType(ToolType.TITLE_GENERATOR);
        entry.setProviderName(provider);
        entry.setSuccess(success);
        entry.setFailureReason(failureReason);
        entry.setInputTokens(inputTokens);
        entry.setOutputTokens(outputTokens);
        entry.setLatencyMs(latencyMs);
        return entry;
    }

    @Test
    void splitsRowsIntoSuccessFailureAndThrottledBuckets() {
        when(logRepository.findByCreatedAtGreaterThanEqual(any())).thenReturn(List.of(
            row("openai-compatible", true, null, 10, 20, 1000),
            row("openai-compatible", true, null, 5, 15, 2000),
            row("openai-compatible", false, "AiGenerationException", 0, 0, 500),
            row("openai-compatible", false, AiUsageGuard.RATE_LIMITED, 0, 0, 0),
            row("openai-compatible", false, AiUsageGuard.DAILY_CEILING_REACHED, 0, 0, 0)));

        AiUsageSummaryResponse summary = service.getTodaySummary();

        assertThat(summary.totalCalls()).isEqualTo(5);
        assertThat(summary.successCount()).isEqualTo(2);
        assertThat(summary.failureCount()).isEqualTo(1);
        assertThat(summary.throttledCount()).isEqualTo(2);
    }

    @Test
    void sumsTokensOnlyAcrossAllRowsAndAveragesLatencyOverSuccessesOnly() {
        when(logRepository.findByCreatedAtGreaterThanEqual(any())).thenReturn(List.of(
            row("openai-compatible", true, null, 10, 20, 1000),
            row("openai-compatible", true, null, 30, 40, 3000),
            row("openai-compatible", false, "AiGenerationException", 0, 0, 9999))); // failed calls don't count toward the latency average

        AiUsageSummaryResponse summary = service.getTodaySummary();

        assertThat(summary.totalInputTokens()).isEqualTo(40);
        assertThat(summary.totalOutputTokens()).isEqualTo(60);
        assertThat(summary.averageLatencyMs()).isEqualTo(2000.0);
    }

    @Test
    void groupsCallCountsByProvider() {
        when(logRepository.findByCreatedAtGreaterThanEqual(any())).thenReturn(List.of(
            row("openai-compatible", true, null, 1, 1, 100),
            row("openai-compatible", true, null, 1, 1, 100),
            row("anthropic", true, null, 1, 1, 100)));

        AiUsageSummaryResponse summary = service.getTodaySummary();

        assertThat(summary.callsByProvider()).containsEntry("openai-compatible", 2L);
        assertThat(summary.callsByProvider()).containsEntry("anthropic", 1L);
    }

    @Test
    void emptyDayReturnsAllZeroesNotAnError() {
        when(logRepository.findByCreatedAtGreaterThanEqual(any())).thenReturn(List.of());

        AiUsageSummaryResponse summary = service.getTodaySummary();

        assertThat(summary.totalCalls()).isZero();
        assertThat(summary.averageLatencyMs()).isZero();
        assertThat(summary.callsByProvider()).isEmpty();
    }

    @Test
    void exposesTheConfiguredDailyCeilingForContext() {
        when(logRepository.findByCreatedAtGreaterThanEqual(any())).thenReturn(List.of());

        AiUsageSummaryResponse summary = service.getTodaySummary();

        assertThat(summary.dailyCallCeiling()).isEqualTo(200);
    }

    // ---- Phase 38: getSignalSummary() ----

    private AiGenerationLog successfulRow(ToolType toolType, OutcomeSignal signal) {
        AiGenerationLog entry = new AiGenerationLog();
        entry.setToolType(toolType);
        entry.setProviderName("openai-compatible");
        entry.setSuccess(true);
        entry.setOutcomeSignal(signal);
        return entry;
    }

    @Test
    void signalSummaryBreaksDownEachToolsSuccessfulGenerationsBySignal() {
        when(logRepository.findBySuccessTrue()).thenReturn(List.of(
            successfulRow(ToolType.TITLE_GENERATOR, OutcomeSignal.COPIED),
            successfulRow(ToolType.TITLE_GENERATOR, OutcomeSignal.COPIED),
            successfulRow(ToolType.TITLE_GENERATOR, OutcomeSignal.REFINED),
            successfulRow(ToolType.TITLE_GENERATOR, OutcomeSignal.REGENERATED),
            successfulRow(ToolType.TITLE_GENERATOR, null)));

        AiSignalSummaryResponse summary = service.getSignalSummary();
        ToolSignalBreakdown titles = summary.byTool().get("TITLE_GENERATOR");

        assertThat(titles.successfulGenerations()).isEqualTo(5);
        assertThat(titles.copied()).isEqualTo(2);
        assertThat(titles.refined()).isEqualTo(1);
        assertThat(titles.regenerated()).isEqualTo(1);
        assertThat(titles.noSignalYet()).isEqualTo(1);
    }

    @Test
    void signalSummaryKeepsEachToolsBreakdownSeparate() {
        when(logRepository.findBySuccessTrue()).thenReturn(List.of(
            successfulRow(ToolType.TITLE_GENERATOR, OutcomeSignal.COPIED),
            successfulRow(ToolType.DESCRIPTION_GENERATOR, OutcomeSignal.REFINED),
            successfulRow(ToolType.DESCRIPTION_GENERATOR, OutcomeSignal.REFINED)));

        AiSignalSummaryResponse summary = service.getSignalSummary();

        assertThat(summary.byTool().get("TITLE_GENERATOR").successfulGenerations()).isEqualTo(1);
        assertThat(summary.byTool().get("DESCRIPTION_GENERATOR").successfulGenerations()).isEqualTo(2);
        assertThat(summary.byTool().get("DESCRIPTION_GENERATOR").refined()).isEqualTo(2);
    }

    @Test
    void signalSummaryOmitsToolsWithNoSuccessfulGenerationsRatherThanZeroingThem() {
        when(logRepository.findBySuccessTrue()).thenReturn(List.of());

        AiSignalSummaryResponse summary = service.getSignalSummary();

        assertThat(summary.byTool()).isEmpty();
    }
}
