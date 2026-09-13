-- Activation decided in docs/08-data-verification-report.md §8
-- (Deep Verification Pass 2, 2026-09-13). All 10 rows activated at
-- honest, per-row confidence (6 MEDIUM, 4 LOW -- none fabricated).
-- SNIPER_SCOPE maps to BGMI's 8x tier specifically; SCOPE_3X to its
-- 3x tier specifically. BGMI's 2x and 6x tiers have no row in this
-- 5-value schema -- a documented gap, not a guess.

INSERT INTO bgmi_sensitivity_profile
  (gyroscope_usage, scope_level, recommended_min, recommended_max, recommended_starting_value,
   confidence, source_reference, last_verified_at, active)
VALUES
  ('GYRO_ON', 'NO_SCOPE', 300, 400, 350, 'MEDIUM',
   'RaidGG (2026-03-14, named-pro settings + tested across 5 devices/70+ matches) and Sportskeeda agree exactly on 300-400%.',
   '2026-09-13', true),

  ('GYRO_ON', 'RED_DOT', 300, 400, 350, 'MEDIUM',
   'RaidGG and Sportskeeda agree exactly on 300-400%.',
   '2026-09-13', true),

  ('GYRO_ON', 'SCOPE_3X', 220, 300, 260, 'MEDIUM',
   'RaidGG (220-300%, primary, better-sourced) and Sportskeeda (170-250%, secondary, unsourced/2021) partially overlap; RaidGG range used as the stronger source.',
   '2026-09-13', true),

  ('GYRO_ON', 'SCOPE_4X_ACOG', 180, 230, 205, 'MEDIUM',
   'RaidGG (180-250%) and Sportskeeda (180-230%) overlap strongly; intersection used.',
   '2026-09-13', true),

  ('GYRO_ON', 'SNIPER_SCOPE', 70, 100, 85, 'MEDIUM',
   'Mapped to BGMI''s 8x tier specifically. RaidGG (70-100%) and Sportskeeda (70-110%) overlap closely; intersection used. BGMI''s 6x tier is unmapped in this schema.',
   '2026-09-13', true),

  ('GYRO_OFF', 'NO_SCOPE', 130, 150, 140, 'MEDIUM',
   'Mapped to BGMI''s TPP camera sensitivity. RaidGG and BlueStacks agree exactly on 130-150% (TPP). FPP variant (110-130%) not represented -- schema has no TPP/FPP axis.',
   '2026-09-13', true),

  ('GYRO_OFF', 'RED_DOT', 60, 75, 68, 'LOW',
   'RaidGG only (single well-documented source: named pros, explicit multi-device/multi-match testing) -- real evidence, but below this report''s two-source bar for MEDIUM.',
   '2026-09-13', true),

  ('GYRO_OFF', 'SCOPE_3X', 28, 35, 32, 'LOW',
   'RaidGG only, mapped to its "3x Scope" ADS figure specifically (its separate "2x Scope" figure, 45-60%, is unmapped in this schema).',
   '2026-09-13', true),

  ('GYRO_OFF', 'SCOPE_4X_ACOG', 22, 30, 26, 'LOW',
   'RaidGG only.',
   '2026-09-13', true),

  ('GYRO_OFF', 'SNIPER_SCOPE', 10, 15, 13, 'LOW',
   'RaidGG only, mapped to its "8x Scope" ADS figure specifically (its "6x Scope" figure, 18-25%, is unmapped in this schema).',
   '2026-09-13', true);
