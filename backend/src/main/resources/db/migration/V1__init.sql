-- MVP core only. Commerce/affiliate/admin tables are deliberately
-- deferred to later migrations (V5+) per the founder's lean-MVP
-- directive — see docs/07-phase2-system-design.md §2.

CREATE TABLE app_user (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    google_subject_id VARCHAR(255) UNIQUE,
    deleted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE profile (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE REFERENCES app_user(id),
    display_name VARCHAR(255),
    avatar_url VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE tool (
    id UUID PRIMARY KEY,
    tool_type VARCHAR(64) NOT NULL UNIQUE,   -- enum identifier, e.g. KD_CALCULATOR
    slug VARCHAR(64) NOT NULL UNIQUE,        -- URL-facing, e.g. kd-calculator
    name VARCHAR(255) NOT NULL,
    category VARCHAR(64),
    premium_only BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE tool_usage (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES app_user(id),
    tool_type VARCHAR(64) NOT NULL,
    input_summary JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_tool_usage_user ON tool_usage(user_id);

CREATE TABLE generated_result (
    id UUID PRIMARY KEY,
    tool_type VARCHAR(64) NOT NULL,
    input_json JSONB NOT NULL,
    output_json JSONB NOT NULL,
    share_token VARCHAR(32) NOT NULL UNIQUE, -- UNIQUE already creates the index; no separate index needed
    user_id UUID REFERENCES app_user(id),
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_generated_result_expires_at ON generated_result(expires_at);

CREATE TABLE sensitivity_game_profile (
    game VARCHAR(32) PRIMARY KEY,
    yaw_constant DOUBLE PRECISION NOT NULL,
    formula_version VARCHAR(32) NOT NULL,
    source_reference VARCHAR(500) NOT NULL,
    last_verified_at TIMESTAMP,
    active BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE bgmi_sensitivity_profile (
    gyroscope_usage VARCHAR(16) NOT NULL,   -- GYRO_ON | GYRO_OFF
    scope_level VARCHAR(16) NOT NULL,       -- NO_SCOPE | RED_DOT | SCOPE_3X | SCOPE_4X_ACOG | SNIPER_SCOPE
    recommended_min INT,
    recommended_max INT,
    recommended_starting_value INT,
    confidence VARCHAR(20) NOT NULL,        -- HIGH | MEDIUM | LOW | INSUFFICIENT_DATA
    source_reference VARCHAR(500),
    last_verified_at TIMESTAMP,
    active BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (gyroscope_usage, scope_level)
);
