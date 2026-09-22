package com.extremis.hub.ai;

import lombok.Getter;
import lombok.Setter;

/**
 * Any chat-completion-shaped endpoint whose request/response look like
 * OpenAI's (messages array in, choices[0].message.content out) --
 * FreeModel and Free.ai today; Cloudflare Workers AI or OpenRouter are
 * config-only swaps. Two conventions were found live: FreeModel's real
 * path is {base}/chat/completions, Free.ai's is {base}/v1/chat/ (note
 * the trailing slash, no "completions") -- so chatUrl is the exact,
 * complete URL to POST to, not a base the code appends a suffix to.
 * Deliberately no defaults for chatUrl/apiKey/model -- an unvetted
 * third-party host must be named explicitly, never assumed. Two
 * instances of this same class are bound at different prefixes (see
 * AiProviderConfig) so a second backend is config-only, no new class.
 */
@Getter
@Setter
public class OpenAiCompatibleProperties {

    /** The exact chat-completion endpoint URL, e.g. https://host/v1/chat/completions. */
    private String chatUrl;
    private String apiKey;
    private String model;

    private int timeoutSeconds = 30;

    /**
     * Free "reasoning" models spend part of max_tokens on hidden thinking;
     * measured on the live gateway, a 900-token budget returned EMPTY
     * content (finish_reason=length) 2 runs in 3, while 3000 gave valid
     * JSON 5/5. Callers' requested ceiling is raised to at least this.
     */
    private int maxTokensFloor = 3000;
}
