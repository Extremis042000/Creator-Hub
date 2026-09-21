package com.extremis.hub.ai;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Backed by extremis.ai.* (env vars ANTHROPIC_API_KEY, ANTHROPIC_MODEL, etc.). */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "extremis.ai")
public class AiProperties {

    /** Kill switch -- false leaves AI generation off even with a key set, no redeploy needed beyond an env change. */
    private boolean enabled = true;

    /** Left blank until the founder supplies a real Anthropic API key -- see AiProviderConfig. */
    private String apiKey;

    /**
     * Defaults to the current flagship model. Cheaper tiers exist
     * (e.g. claude-sonnet-5) and are a cost decision for the founder,
     * not something to downgrade silently -- override via ANTHROPIC_MODEL.
     */
    private String model = "claude-opus-5";

    /** low | medium | high | xhigh | max -- low suits short creative-text generation. */
    private String effort = "low";

    private int timeoutSeconds = 45;
    private int maxRetries = 1;
}
