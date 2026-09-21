package com.extremis.hub.ai;

/** Deliberately carries no prompt/response text -- user input shouldn't end up in logs or error reports. */
public class AiGenerationException extends RuntimeException {

    public AiGenerationException(String message) {
        super(message);
    }

    public AiGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
