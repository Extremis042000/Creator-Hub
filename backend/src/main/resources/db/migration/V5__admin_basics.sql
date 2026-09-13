-- Phase 17 (Admin basics). Only feature_flag is added here -- Product
-- and AffiliateProduct tables stay deferred to Phases 18/19/22 per
-- the lean-MVP migration progression; building admin screens for
-- tables that don't exist yet would be exactly the scope creep that
-- progression exists to avoid. No admin_user table: see AdminAccessProperties
-- for why an env-var allowlist is used instead for this phase.

CREATE TABLE feature_flag (
    key VARCHAR(128) PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT false,
    rollout_percent INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);
