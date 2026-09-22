package com.extremis.hub.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CompositeAiGenerationProviderTest {

    private static final AiGenerationRequest REQUEST = new AiGenerationRequest("s", "u", 100);

    /** A fake delegate that always succeeds, tagged so calls can be attributed to it. */
    private static AiGenerationProvider ok(String tag, AtomicInteger callCount) {
        return new AiGenerationProvider() {
            @Override public String getProviderName() { return tag; }
            @Override public String getModelName() { return tag; }
            @Override public AiGenerationResult generate(AiGenerationRequest request) {
                callCount.incrementAndGet();
                return new AiGenerationResult(tag, 1, 1, tag);
            }
        };
    }

    private static AiGenerationProvider failing(String tag, AtomicInteger callCount) {
        return new AiGenerationProvider() {
            @Override public String getProviderName() { return tag; }
            @Override public String getModelName() { return tag; }
            @Override public AiGenerationResult generate(AiGenerationRequest request) {
                callCount.incrementAndGet();
                throw new AiGenerationException(tag + " failed");
            }
        };
    }

    @Test
    void distributesConsecutiveCallsAcrossDelegatesInRotation() {
        AtomicInteger aCalls = new AtomicInteger();
        AtomicInteger bCalls = new AtomicInteger();
        CompositeAiGenerationProvider composite =
            new CompositeAiGenerationProvider(List.of(ok("a", aCalls), ok("b", bCalls)));

        for (int i = 0; i < 10; i++) {
            composite.generate(REQUEST);
        }

        // Genuinely round-robin, not "always the first that works": each
        // delegate takes half the traffic, not 10/0 or a skewed split.
        assertThat(aCalls.get()).isEqualTo(5);
        assertThat(bCalls.get()).isEqualTo(5);
    }

    @Test
    void fallsOverToTheNextDelegateWhenOneFails() {
        AtomicInteger failCalls = new AtomicInteger();
        AtomicInteger okCalls = new AtomicInteger();
        CompositeAiGenerationProvider composite =
            new CompositeAiGenerationProvider(List.of(failing("bad", failCalls), ok("good", okCalls)));

        AiGenerationResult result = composite.generate(REQUEST);

        assertThat(result.text()).isEqualTo("good");
        assertThat(failCalls.get()).isEqualTo(1);
        assertThat(okCalls.get()).isEqualTo(1);
    }

    @Test
    void throwsOnlyAfterEveryDelegateHasFailed() {
        AtomicInteger aCalls = new AtomicInteger();
        AtomicInteger bCalls = new AtomicInteger();
        CompositeAiGenerationProvider composite =
            new CompositeAiGenerationProvider(List.of(failing("a", aCalls), failing("b", bCalls)));

        assertThatThrownBy(() -> composite.generate(REQUEST))
            .isInstanceOf(AiGenerationException.class)
            .hasMessageContaining("All 2 AI providers failed");
        assertThat(aCalls.get()).isEqualTo(1);
        assertThat(bCalls.get()).isEqualTo(1);
    }

    @Test
    void rejectsAnEmptyDelegateList() {
        assertThatThrownBy(() -> new CompositeAiGenerationProvider(List.of()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reportsRoundRobinAsItsProviderNameAndListsEachDelegateInItsModelName() {
        AiGenerationProvider composite = new CompositeAiGenerationProvider(
            List.of(ok("alpha", new AtomicInteger()), ok("beta", new AtomicInteger())));

        assertThat(composite.getProviderName()).isEqualTo("round-robin");
        assertThat(composite.getModelName()).isEqualTo("alpha/alpha, beta/beta");
    }
}
