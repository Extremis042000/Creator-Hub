package com.extremis.hub.ai;

/** One prior turn in a Phase 29 refine conversation -- see RefineSession. */
public record ConversationTurn(Role role, String content) {

    public enum Role {
        USER, ASSISTANT
    }
}
