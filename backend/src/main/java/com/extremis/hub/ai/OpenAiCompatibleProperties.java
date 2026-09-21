package com.extremis.hub.ai;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Any OpenAI-style /chat/completions endpoint (FreeModel today; Cloudflare
 * Workers AI or OpenRouter are config-only swaps). Deliberately no
 * defaults for baseUrl/apiKey/model -- an unvetted third-party host must
 * be named explicitly, never assumed.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "extremis.ai.compat")
public class OpenAiCompatibleProperties {

    /** e.g. https://host/v1 -- the provider appends /chat/completions. */
    private String baseUrl;
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
