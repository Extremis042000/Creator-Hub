package com.extremis.hub.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

/**
 * One row per AI generation attempt -- success, failure, or throttled
 * -- for Phase 28's cost/abuse visibility. Deliberately carries no
 * prompt or response text, only metadata, matching
 * AiGenerationException's own no-user-content rule. See AiUsageGuard.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
public class AiGenerationLog extends Auditable {

    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "tool_type", nullable = false)
    private ToolType toolType;

    @Column(name = "provider_name", nullable = false)
    private String providerName;

    @Column(name = "model_name")
    private String modelName;

    /** The model that actually served the call -- gateways route, so it can differ from modelName. Null for a throttled attempt (no call was made). */
    @Column(name = "served_by")
    private String servedBy;

    @Column(name = "input_tokens", nullable = false)
    private long inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private long outputTokens;

    /** 0 for a throttled attempt -- no call was made, so nothing to time. */
    @Column(name = "latency_ms", nullable = false)
    private long latencyMs;

    @Column(nullable = false)
    private boolean success;

    /** Short code only -- an exception class name, or AiUsageGuard.RATE_LIMITED/DAILY_CEILING_REACHED -- never raw error text. */
    @Column(name = "failure_reason")
    private String failureReason;

    /** Phase 37: null until (if ever) a signal is recorded -- see OutcomeSignal and GenerationSignalService. */
    @Enumerated(EnumType.STRING)
    @Column(name = "outcome_signal")
    private OutcomeSignal outcomeSignal;
}
