package com.extremis.hub.ai;

/** Deliberately carries no prompt/response text -- same discipline as AiGenerationException. */
public class ImageGenerationException extends RuntimeException {

    public ImageGenerationException(String message) {
        super(message);
    }

    public ImageGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
