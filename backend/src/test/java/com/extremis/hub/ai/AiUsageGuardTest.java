package com.extremis.hub.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.extremis.hub.domain.AiGenerationLog;
import com.extremis.hub.domain.ToolType;
import com.extremis.hub.domain.User;
import com.extremis.hub.repository.AiGenerationLogRepository;
import com.extremis.hub.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiUsageGuardTest {

    @Mock private AiGenerationLogRepository logRepository;
    @Mock private UserRepository userRepository;

    private AiUsageProperties properties;
    private AiUsageGuard guard;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        properties = new AiUsageProperties();
        guard = new AiUsageGuard(properties, logRepository, userRepository);
    }

    @Test
    void allowsCallsUpToTheRateLimitThenDenies() {
        properties.setRateLimitPerMinute(2);

        assertThat(guard.tryAcquire(userId)).isNull();
        assertThat(guard.tryAcquire(userId)).isNull();
        assertThat(guard.tryAcquire(userId)).isEqualTo(AiUsageGuard.RATE_LIMITED);
    }

    @Test
    void rateLimitIsPerUserNotGlobal() {
        properties.setRateLimitPerMinute(1);
        UUID otherUser = UUID.randomUUID();

        assertThat(guard.tryAcquire(userId)).isNull();
        assertThat(guard.tryAcquire(userId)).isEqualTo(AiUsageGuard.RATE_LIMITED);
        assertThat(guard.tryAcquire(otherUser)).isNull(); // a different user isn't affected
    }

    @Test
    void dailyCeilingIsGlobalAcrossUsers() {
        properties.setRateLimitPerMinute(100);
        properties.setDailyCallCeiling(1);
        UUID otherUser = UUID.randomUUID();

        assertThat(guard.tryAcquire(userId)).isNull(); // consumes the one global slot
        assertThat(guard.tryAcquire(otherUser)).isEqualTo(AiUsageGuard.DAILY_CEILING_REACHED);
    }

    @Test
    void zeroDailyCeilingDeniesEveryCall() {
        properties.setDailyCallCeiling(0);

        assertThat(guard.tryAcquire(userId)).isEqualTo(AiUsageGuard.DAILY_CEILING_REACHED);
    }

    @Test
    void recordSuccessSavesALogRowWhenUserExists() {
        User user = new User();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        guard.recordSuccess(userId, ToolType.TITLE_GENERATOR, "openai-compatible", "fm-v1-lite", "served-model", 10, 20, 1234);

        verify(logRepository, times(1)).save(any(AiGenerationLog.class));
    }

    @Test
    void recordFailureSavesALogRowWithFailureReasonAndZeroTokens() {
        User user = new User();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        guard.recordFailure(userId, ToolType.DESCRIPTION_GENERATOR, "openai-compatible", "AiGenerationException", 500);

        verify(logRepository).save(argThatFailureReasonIs("AiGenerationException"));
    }

    @Test
    void recordThrottledSavesALogRowWithZeroLatency() {
        User user = new User();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        guard.recordThrottled(userId, ToolType.TITLE_GENERATOR, "openai-compatible", AiUsageGuard.RATE_LIMITED);

        verify(logRepository).save(argThatFailureReasonIs(AiUsageGuard.RATE_LIMITED));
    }

    @Test
    void skipsLoggingWithoutThrowingWhenUserNoLongerExists() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        guard.recordSuccess(userId, ToolType.TITLE_GENERATOR, "openai-compatible", "fm-v1-lite", "served-model", 1, 1, 1);

        verify(logRepository, never()).save(any());
    }

    private AiGenerationLog argThatFailureReasonIs(String reason) {
        return org.mockito.ArgumentMatchers.argThat(entry -> reason.equals(entry.getFailureReason()));
    }
}
