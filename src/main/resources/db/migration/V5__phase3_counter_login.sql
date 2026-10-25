-- ============================================================================
-- Phase 3 (cont.): two-mode login
--
--   Mode 1 (counter): business login code + counter code + counter password
--   Mode 2 (admin):   business login code (or email) + admin password
--
-- This adds:
--   1. businesses.login_code  — short, unique, human-typable business identifier
--   2. counters.password_hash — each counter has its own password (set by owner)
--
-- Runs on existing data: backfill a login_code for every business from a slug
-- of its id, then enforce NOT NULL + UNIQUE. Existing counters get a NULL
-- password_hash (login disabled until the owner sets one).
-- ============================================================================

-- 1. Business login code -----------------------------------------------------
ALTER TABLE businesses ADD COLUMN login_code TEXT;

-- Backfill: first 8 hex chars of the id, uppercased, prefixed B- (collision-safe
-- enough for existing rows; new businesses get a generated code in the app).
UPDATE businesses
SET login_code = 'B-' || UPPER(LEFT(REPLACE(id::text, '-', ''), 8))
WHERE login_code IS NULL;

ALTER TABLE businesses ALTER COLUMN login_code SET NOT NULL;
ALTER TABLE businesses ADD CONSTRAINT uq_businesses_login_code UNIQUE (login_code);

-- 2. Counter password --------------------------------------------------------
ALTER TABLE counters ADD COLUMN password_hash TEXT;
