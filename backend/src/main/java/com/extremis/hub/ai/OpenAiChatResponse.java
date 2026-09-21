package com.extremis.hub.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** The subset of an OpenAI-style chat completion response we read; everything else is ignored. */
@JsonIgnoreProperties(ignoreUnknown = true)
record OpenAiChatResponse(String model, List<Choice> choices, Usage usage) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Choice(Message message, @JsonProperty("finish_reason") String finishReason) {
    }

    /** content can legitimately be null (e.g. reasoning consumed the whole token budget). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Message(String content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Usage(
        @JsonProperty("prompt_tokens") Long promptTokens,
        @JsonProperty("completion_tokens") Long completionTokens) {
    }
}
