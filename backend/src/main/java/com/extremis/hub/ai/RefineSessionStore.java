package com.extremis.hub.ai;

import com.extremis.hub.domain.ToolType;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Phase 29: in-memory store for RefineSession, closed automatically by
 * inactivity or a small turn cap -- see
 * docs/decisions/07-ai-generation-initiative.md §6. A single Render
 * instance, so a ConcurrentHashMap is enough; no need for a
 * distributed cache for something this short-lived.
 */
@Component
public class RefineSessionStore {

    public static final Duration SESSION_TTL = Duration.ofMinutes(10);
    public static final int MAX_REFINE_TURNS = 6;

    private final Map<UUID, RefineSession> sessions = new ConcurrentHashMap<>();

    public RefineSession create(UUID userId, ToolType toolType, String systemPrompt, String initialAssistantText) {
        sweepExpired();
        RefineSession session = new RefineSession(userId, toolType, systemPrompt, initialAssistantText);
        sessions.put(session.getId(), session);
        return session;
    }

    /** Empty if the session doesn't exist, expired, belongs to a different user, or is for a different tool -- callers must not distinguish which, to avoid leaking session existence across users. */
    public Optional<RefineSession> find(UUID sessionId, UUID userId, ToolType toolType) {
        sweepExpired();
        RefineSession session = sessions.get(sessionId);
        if (session == null || !session.getUserId().equals(userId) || session.getToolType() != toolType) {
            return Optional.empty();
        }
        return Optional.of(session);
    }

    private void sweepExpired() {
        sessions.entrySet().removeIf(e -> e.getValue().isExpired(SESSION_TTL));
    }
}
