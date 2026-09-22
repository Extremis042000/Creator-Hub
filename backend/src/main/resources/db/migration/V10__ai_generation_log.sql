-- Phase 28 (AI cost & abuse controls). One row per AI generation
-- attempt -- success, failure, or throttled -- never the prompt or
-- response text itself, only metadata, matching AiGenerationException's
-- own no-user-content rule. Powers the admin "AI usage today" view and
-- the daily call ceiling circuit-breaker in AiUsageGuard.

CREATE TABLE ai_generation_log (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id),
    tool_type VARCHAR(64) NOT NULL,
    provider_name VARCHAR(64) NOT NULL,
    model_name VARCHAR(128),
    served_by VARCHAR(128),
    input_tokens BIGINT NOT NULL DEFAULT 0,
    output_tokens BIGINT NOT NULL DEFAULT 0,
    latency_ms BIGINT NOT NULL DEFAULT 0,
    success BOOLEAN NOT NULL,
    failure_reason VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_ai_generation_log_created_at ON ai_generation_log(created_at);
CREATE INDEX idx_ai_generation_log_user ON ai_generation_log(user_id);
