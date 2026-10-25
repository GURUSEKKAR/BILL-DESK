-- ============================================================================
-- Phase 3 schema: multi-counter hierarchy (lite)
--
-- One business, many counters. OWNER sees everything; CASHIER sees only their
-- own counter. Customers stay shared across the business; reminders stay
-- business-level.
--
-- This migration is written to run on EXISTING data (V1 + V2 + V3):
--   1. create counters table
--   2. seed one default "Main" (C1) counter per existing business
--   3. add role + counter_id to users (existing users -> OWNER, no counter)
--   4. add nullable counter columns to invoices / payments / audit_log,
--      backfill them with the default counter, then set NOT NULL where safe
--   5. re-key invoice_number_seq from (business, year) to (business, counter, year)
--   6. add credit_limit_paise to customers (Phase 3b)
--   7. add the per-counter indexes
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Counters
-- ----------------------------------------------------------------------------
CREATE TABLE counters (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id),
    name        TEXT NOT NULL,
    code        TEXT NOT NULL,
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (business_id, code)
);
CREATE INDEX idx_counters_business ON counters (business_id);

-- ----------------------------------------------------------------------------
-- 2. Seed a default "Main" counter (code C1) for every existing business
-- ----------------------------------------------------------------------------
INSERT INTO counters (business_id, name, code, active)
SELECT b.id, 'Main', 'C1', TRUE
FROM businesses b;

-- ----------------------------------------------------------------------------
-- 3. Users: business link + role + optional counter assignment
--    Existing users are business owners -> OWNER, counter_id stays NULL.
--    Add business_id so CASHIER users (who do not own a business) still know
--    which business they belong to.
-- ----------------------------------------------------------------------------
ALTER TABLE users ADD COLUMN business_id UUID REFERENCES businesses(id);
UPDATE users u
SET business_id = b.id
FROM businesses b
WHERE b.owner_user_id = u.id
  AND u.business_id IS NULL;
ALTER TABLE users ALTER COLUMN business_id SET NOT NULL;
CREATE INDEX idx_users_business ON users (business_id);

-- The users <-> businesses references are mutually dependent (a user points at
-- its business and a business points at its owner user). Make both FKs
-- DEFERRABLE INITIALLY DEFERRED so a fresh owner+business pair can be inserted
-- in a single transaction regardless of statement order.
ALTER TABLE users
    DROP CONSTRAINT users_business_id_fkey,
    ADD CONSTRAINT users_business_id_fkey FOREIGN KEY (business_id)
        REFERENCES businesses(id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE businesses
    DROP CONSTRAINT businesses_owner_user_id_fkey,
    ADD CONSTRAINT businesses_owner_user_id_fkey FOREIGN KEY (owner_user_id)
        REFERENCES users(id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE users ADD COLUMN role TEXT;
UPDATE users SET role = 'OWNER' WHERE role IS NULL;
ALTER TABLE users ALTER COLUMN role SET NOT NULL;
ALTER TABLE users ALTER COLUMN role SET DEFAULT 'OWNER';
ALTER TABLE users ADD CONSTRAINT chk_users_role CHECK (role IN ('OWNER', 'CASHIER'));

ALTER TABLE users ADD COLUMN counter_id UUID REFERENCES counters(id);
ALTER TABLE users ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
-- A CASHIER must have a counter; an OWNER must not. Enforced in the app layer,
-- but we add a defensive check too.
ALTER TABLE users ADD CONSTRAINT chk_users_counter
    CHECK (
        (role = 'OWNER'   AND counter_id IS NULL) OR
        (role = 'CASHIER' AND counter_id IS NOT NULL)
    );

-- ----------------------------------------------------------------------------
-- 4a. Invoices: counter_id + created_by  (nullable -> backfill -> NOT NULL)
-- ----------------------------------------------------------------------------
ALTER TABLE invoices ADD COLUMN counter_id UUID REFERENCES counters(id);
ALTER TABLE invoices ADD COLUMN created_by UUID REFERENCES users(id);

-- Backfill every existing invoice onto its business's default (C1) counter.
UPDATE invoices i
SET counter_id = c.id
FROM counters c
WHERE c.business_id = i.business_id
  AND c.code = 'C1'
  AND i.counter_id IS NULL;

-- created_by is best-effort: point it at the business owner where we can.
UPDATE invoices i
SET created_by = b.owner_user_id
FROM businesses b
WHERE b.id = i.business_id
  AND i.created_by IS NULL;

ALTER TABLE invoices ALTER COLUMN counter_id SET NOT NULL;
-- created_by stays nullable: historical rows may have no reliable actor.

-- ----------------------------------------------------------------------------
-- 4b. Payments: counter_id + received_by  (nullable -> backfill -> NOT NULL)
-- ----------------------------------------------------------------------------
ALTER TABLE payments ADD COLUMN counter_id UUID REFERENCES counters(id);
ALTER TABLE payments ADD COLUMN received_by UUID REFERENCES users(id);

-- A payment belongs to the same counter as its invoice.
UPDATE payments p
SET counter_id = i.counter_id
FROM invoices i
WHERE i.id = p.invoice_id
  AND p.counter_id IS NULL;

UPDATE payments p
SET received_by = b.owner_user_id
FROM businesses b
WHERE b.id = p.business_id
  AND p.received_by IS NULL;

ALTER TABLE payments ALTER COLUMN counter_id SET NOT NULL;

-- ----------------------------------------------------------------------------
-- 4c. Audit log: user_id + counter_id (both nullable — system actions exist)
--     audit_log already has actor_user_id; we add counter_id for "which counter".
-- ----------------------------------------------------------------------------
ALTER TABLE audit_log ADD COLUMN counter_id UUID;

-- ----------------------------------------------------------------------------
-- 5. Re-key invoice_number_seq: (business, year) -> (business, counter, year)
--    Give numbers like C1-2026-000001. Existing numbers are left untouched.
--
--    The old PK rows carry per-business-per-year counters. We migrate them onto
--    the default counter so continuing numbering does not clash with history.
-- ----------------------------------------------------------------------------
ALTER TABLE invoice_number_seq ADD COLUMN counter_id UUID REFERENCES counters(id);

UPDATE invoice_number_seq s
SET counter_id = c.id
FROM counters c
WHERE c.business_id = s.business_id
  AND c.code = 'C1'
  AND s.counter_id IS NULL;

ALTER TABLE invoice_number_seq ALTER COLUMN counter_id SET NOT NULL;

ALTER TABLE invoice_number_seq DROP CONSTRAINT invoice_number_seq_pkey;
ALTER TABLE invoice_number_seq
    ADD CONSTRAINT invoice_number_seq_pkey PRIMARY KEY (business_id, counter_id, year);

-- ----------------------------------------------------------------------------
-- 6. Customers: credit limit (Phase 3b). Nullable = no limit enforced.
-- ----------------------------------------------------------------------------
ALTER TABLE customers ADD COLUMN credit_limit_paise BIGINT;

-- ----------------------------------------------------------------------------
-- 7. Per-counter indexes for the scoped list / dashboard queries
-- ----------------------------------------------------------------------------
CREATE INDEX idx_invoices_biz_counter_created
    ON invoices (business_id, counter_id, created_at);
CREATE INDEX idx_invoices_biz_counter_status_due
    ON invoices (business_id, counter_id, status, due_date);
CREATE INDEX idx_payments_biz_counter ON payments (business_id, counter_id);
