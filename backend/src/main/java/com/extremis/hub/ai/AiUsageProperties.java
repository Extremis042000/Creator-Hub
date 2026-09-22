package com.extremis.hub.ai;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Backed by extremis.ai.usage.* -- Phase 28's cost/abuse controls. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "extremis.ai.usage")
public class AiUsageProperties {

    /** Per-user AI calls allowed per rolling 60s window -- defends against one entitled account hammering the AI path. */
    private int rateLimitPerMinute = 5;

    /**
     * Global daily AI-call ceiling across all users -- this product's
     * equivalent of Triage Desk's --max-budget-usd. Calls, not dollars:
     * FreeModel/Free.ai don't publish per-call pricing (see
     * decisions/07-ai-generation-initiative.md §8-10), so a call count
     * is the honest, measurable proxy actually available. Once hit, the
     * AI path auto-disables (falls back to templates for everyone)
     * until the next UTC day.
     */
    private int dailyCallCeiling = 200;
}
