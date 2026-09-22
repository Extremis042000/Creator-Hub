package com.extremis.hub.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.extremis.hub.domain.ToolType;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RefineSessionStoreTest {

    private final RefineSessionStore store = new RefineSessionStore();
    private final UUID userId = UUID.randomUUID();

    @Test
    void findReturnsTheSessionForItsOwnerAndToolType() {
        RefineSession session = store.create(userId, ToolType.TITLE_GENERATOR, "sys", "initial");

        assertThat(store.find(session.getId(), userId, ToolType.TITLE_GENERATOR)).contains(session);
    }

    @Test
    void findIsEmptyForAWrongUser() {
        RefineSession session = store.create(userId, ToolType.TITLE_GENERATOR, "sys", "initial");

        assertThat(store.find(session.getId(), UUID.randomUUID(), ToolType.TITLE_GENERATOR)).isEmpty();
    }

    @Test
    void findIsEmptyForAWrongToolType() {
        RefineSession session = store.create(userId, ToolType.TITLE_GENERATOR, "sys", "initial");

        assertThat(store.find(session.getId(), userId, ToolType.DESCRIPTION_GENERATOR)).isEmpty();
    }

    @Test
    void findIsEmptyForAnUnknownSessionId() {
        assertThat(store.find(UUID.randomUUID(), userId, ToolType.TITLE_GENERATOR)).isEmpty();
    }

    @Test
    void aFreshSessionIsNotExpiredUnderTheRealTtl() {
        RefineSession session = new RefineSession(userId, ToolType.TITLE_GENERATOR, "sys", "initial");

        assertThat(session.isExpired(RefineSessionStore.SESSION_TTL)).isFalse();
    }

    @Test
    void aSessionIsExpiredOnceItsInactivityWindowHasPassed() throws InterruptedException {
        RefineSession session = new RefineSession(userId, ToolType.TITLE_GENERATOR, "sys", "initial");
        Thread.sleep(5); // guarantees real elapsed time has passed, avoiding a same-instant race against Duration.ZERO

        assertThat(session.isExpired(Duration.ZERO)).isTrue();
    }

    @Test
    void recordExchangeAppendsBothTurnsAndIncrementsTurnCount() {
        RefineSession session = store.create(userId, ToolType.TITLE_GENERATOR, "sys", "initial assistant text");

        session.recordExchange("make it punchier", "revised assistant text");

        assertThat(session.getTurnCount()).isEqualTo(1);
        assertThat(session.historySnapshot()).containsExactly(
            new ConversationTurn(ConversationTurn.Role.ASSISTANT, "initial assistant text"),
            new ConversationTurn(ConversationTurn.Role.USER, "make it punchier"),
            new ConversationTurn(ConversationTurn.Role.ASSISTANT, "revised assistant text"));
    }
}
