-- Phase 18 (Affiliate architecture + disclosure). Admins manage
-- affiliate products via the admin panel; the public site never links
-- to affiliateUrl directly -- it always goes through
-- GET /api/v1/affiliate/{id}/redirect, which logs a click row here
-- and then 302s to the real merchant URL, so click counts are always
-- server-verified rather than trusting the frontend to fire its own
-- analytics event.

CREATE TABLE affiliate_product (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    brand VARCHAR(255),
    category VARCHAR(64),
    price_info VARCHAR(255),
    affiliate_url VARCHAR(2048) NOT NULL,
    merchant VARCHAR(128),
    region VARCHAR(64),
    disclosure_text VARCHAR(1000),
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE affiliate_click (
    id UUID PRIMARY KEY,
    affiliate_product_id UUID NOT NULL REFERENCES affiliate_product(id),
    user_id UUID REFERENCES app_user(id),
    session_ref VARCHAR(128),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_affiliate_click_product ON affiliate_click(affiliate_product_id);
CREATE INDEX idx_affiliate_click_user ON affiliate_click(user_id);
