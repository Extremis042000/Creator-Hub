-- Phase 37 (first-party usage-signal capture). A lightweight outcome
-- signal per successful AI generation, as a proxy for "was this good"
-- entirely from this product's own data (no YouTube-ToS-style question
-- here -- see docs/decisions/09-ai-model-optimization.md SS2.4).
-- NULL means no signal recorded yet (most rows, most of the time --
-- not every generation gets copied, refined, or immediately redone).

ALTER TABLE ai_generation_log ADD COLUMN outcome_signal VARCHAR(32);
