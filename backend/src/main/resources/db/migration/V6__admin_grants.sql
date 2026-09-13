-- Lets an existing admin grant admin access to another already-signed-in
-- user at runtime, without an env-var/redeploy round trip. ADMIN_EMAILS
-- remains the permanent "founder" bootstrap allowlist (can't be revoked
-- via this table, only by editing the env var) -- see AdminAccessProperties
-- and AdminService for why the bootstrap mechanism stays env-var-based
-- while this table handles everything granted afterward.

CREATE TABLE admin_grant (
    user_id UUID PRIMARY KEY REFERENCES app_user(id),
    granted_by_user_id UUID REFERENCES app_user(id),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
