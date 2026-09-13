-- Activation decided in docs/08-data-verification-report.md §8
-- (Deep Verification Pass 2, 2026-09-13). 4 of 5 games activated;
-- Overwatch 2 deliberately held back (active=false) — see that
-- section for the full evidence trail behind every value/decision
-- here.

INSERT INTO sensitivity_game_profile
  (game, yaw_constant, formula_version, source_reference, last_verified_at, active)
VALUES
  ('CSGO', 0.022, 'v1-cm360-ratio',
   'Valve Developer Community wiki, Console Commands page (developer.valvesoftware.com/wiki/Console_commands); cross-confirmed by csdb.gg, totalcsgo.com, csgo-nade.com; empirically verifiable in-game via the m_yaw console command.',
   '2026-09-13', true),

  ('CS2', 0.022, 'v1-cm360-ratio',
   'Same Source-engine m_yaw cvar retained in Source 2; csdb.gg CS2 command page; empirically verifiable in-game.',
   '2026-09-13', true),

  ('VALORANT', 0.07, 'v1-cm360-ratio',
   'Community-derived (no official Riot documentation); confirmed via direct fetch of xp-feed.com and dcprosens.com, cross-checked against aimhub.gg, senslab.pro, sensai.games -- consistent across two research passes, zero contradicting values.',
   '2026-09-13', true),

  ('APEX_LEGENDS', 0.022, 'v1-cm360-ratio',
   'Community-derived, attributed to Respawn''s Source-engine fork retaining the default m_yaw; confirmed via senslab.pro, cross-checked against sensiconverter.com, sensconverterfree.com; not independently verifiable in-game.',
   '2026-09-13', true),

  ('OVERWATCH2', 0.0066, 'v1-cm360-ratio',
   'Community-derived; repeated across dcprosens.com/senslab.pro but NOT confirmed on-page by two direct-fetch attempts on dedicated OW2 pages across two research passes. Held back -- see Data Verification Report section 8.1.',
   '2026-09-13', false);
