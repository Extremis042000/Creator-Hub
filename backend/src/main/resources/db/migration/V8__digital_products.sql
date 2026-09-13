-- Phase 19 (Digital product architecture + secure delivery). Product
-- files are never served from a public/static path -- they live in a
-- secure directory outside the web root (see DigitalProductProperties)
-- and are only reachable through a short-lived, HMAC-signed download
-- token (DownloadTokenService), matching the roadmap phase title
-- ("Spring Boot signed download URLs").
--
-- No real PaymentProvider exists yet (that's Phase 20, gated on the
-- founder completing KYC with a real provider) -- per founder
-- direction, a "test" provider row simulates an instantly-successful
-- payment so the full purchase -> entitlement -> download chain can
-- be built and proven end-to-end now, open to any signed-in user (not
-- just admins). See DigitalProductProperties#testPurchasesEnabled for
-- the kill switch to disable this before Phase 20 lands.
--
-- "orders" is plural (not "order") purely to avoid quoting a reserved
-- SQL keyword everywhere -- no semantic difference from the design doc.

CREATE TABLE product_category (
    id UUID PRIMARY KEY,
    slug VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE product (
    id UUID PRIMARY KEY,
    category_id UUID NOT NULL REFERENCES product_category(id),
    name VARCHAR(255) NOT NULL,
    price_cents BIGINT NOT NULL,
    currency VARCHAR(8) NOT NULL DEFAULT 'USD',
    file_ref VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    deleted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_product_category ON product(category_id);

CREATE TABLE orders (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id),
    status VARCHAR(16) NOT NULL,
    total_cents BIGINT NOT NULL,
    currency VARCHAR(8) NOT NULL DEFAULT 'USD',
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_orders_user ON orders(user_id);

CREATE TABLE order_item (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id),
    product_id UUID NOT NULL REFERENCES product(id),
    price_cents BIGINT NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_order_item_order ON order_item(order_id);
CREATE INDEX idx_order_item_product ON order_item(product_id);

CREATE TABLE payment (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE REFERENCES orders(id),
    provider VARCHAR(32) NOT NULL,
    provider_ref VARCHAR(255),
    status VARCHAR(32) NOT NULL,
    verified_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE user_entitlement (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id),
    entitlement_key VARCHAR(255) NOT NULL,
    source VARCHAR(32) NOT NULL,
    granted_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (user_id, entitlement_key)
);

CREATE INDEX idx_user_entitlement_user ON user_entitlement(user_id);
