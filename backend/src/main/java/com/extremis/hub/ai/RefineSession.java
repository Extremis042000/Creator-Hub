package com.extremis.hub.ai;

import com.extremis.hub.domain.ToolType;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Phase 29: a short-lived, in-memory conversation for "refine this
 * result" follow-ups -- the Tier-3-style pattern from the founder's
 * Triage Desk project, scoped down (see
 * docs/decisions/07-ai-generation-initiative.md §6). Deliberately NOT
 * persisted: a single Render instance, so an in-memory session is
 * enough, and a restart simply ending any open refine sessions is an
 * acceptable loss for something this short-lived -- see
 * RefineSessionStore for the TTL/turn-cap enforcement.
 */
public class RefineSession {

    private final UUID id = UUID.randomUUID();
    private final UUID userId;
    private final ToolType toolType;
    private final String systemPrompt;
    private final Instant createdAt = Instant.now();
    private final List<ConversationTurn> turns = new ArrayList<>();
    private volatile Instant lastActivityAt = createdAt;
    private int turnCount;

    /**
     * Phase 37: the AiGenerationLog row this session's ORIGINAL
     * generate() call created -- null when that call didn't return an
     * id (the user vanished mid-request, a real edge case AiUsageGuard
     * already handles). GenerationSignalService updates that row, not
     * a per-refine-turn one, so "was the original result good" stays
     * one answer per session even after several refine turns.
     */
    private final UUID generationLogId;

    RefineSession(UUID userId, ToolType toolType, String systemPrompt, String initialAssistantText, UUID generationLogId) {
        this.userId = userId;
        this.toolType = toolType;
        this.systemPrompt = systemPrompt;
        this.generationLogId = generationLogId;
        this.turns.add(new ConversationTurn(ConversationTurn.Role.ASSISTANT, initialAssistantText));
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public ToolType getToolType() {
        return toolType;
    }

    public UUID getGenerationLogId() {
        return generationLogId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public int getTurnCount() {
        return turnCount;
    }

    public synchronized List<ConversationTurn> historySnapshot() {
        return List.copyOf(turns);
    }

    /** Appends this exchange and resets the inactivity clock -- call only after a successful refine call. */
    public synchronized void recordExchange(String userMessage, String assistantResponseText) {
        turns.add(new ConversationTurn(ConversationTurn.Role.USER, userMessage));
        turns.add(new ConversationTurn(ConversationTurn.Role.ASSISTANT, assistantResponseText));
        turnCount++;
        lastActivityAt = Instant.now();
    }

    boolean isExpired(Duration ttl) {
        return Instant.now().isAfter(lastActivityAt.plus(ttl));
    }
}
