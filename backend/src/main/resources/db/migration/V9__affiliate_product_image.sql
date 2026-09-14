-- Founder-requested (creator-gear deals research, see
-- 03-development-roadmap.md Phase 25 follow-up): affiliate product
-- cards had no image at all. No legitimate free/open API exists for
-- real-time creator-gear deals (researched: Best Buy/Newegg need a
-- keyed partner API, Slickdeals/camelcamelcamel explicitly prohibit
-- exactly this use case in their own ToS) -- so this stays an optional,
-- admin-supplied image URL rather than an auto-fetched feed.

ALTER TABLE affiliate_product ADD COLUMN image_url VARCHAR(2048);
