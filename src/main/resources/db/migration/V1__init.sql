-- ============================================================================
-- Phase 1 schema: users, businesses, customers, invoices, payments, audit_log
-- Money is stored as integers (paise) to avoid floating-point rounding errors.
-- Every tenant-scoped table carries business_id for row-level isolation.
-- ============================================================================

-- Users (app accounts; one user owns one business in this simplified model)
CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Businesses (the tenant boundary)
CREATE TABLE businesses (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_user_id UUID NOT NULL REFERENCES users(id),
    name          TEXT NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_businesses_owner ON businesses (owner_user_id);

-- Per-business, per-year invoice number counter.
-- Used with INSERT ... ON CONFLICT DO UPDATE ... RETURNING for atomic numbering.
CREATE TABLE invoice_number_seq (
    business_id UUID NOT NULL REFERENCES businesses(id),
    year        INT  NOT NULL,
    next_val    INT  NOT NULL DEFAULT 1,
    PRIMARY KEY (business_id, year)
);

-- Customers (belong to a business)
CREATE TABLE customers (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id),
    name        TEXT NOT NULL,
    email       TEXT,
    phone       TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_customers_business ON customers (business_id);

-- Invoices
CREATE TABLE invoices (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id),
    customer_id UUID NOT NULL REFERENCES customers(id),
    number      TEXT NOT NULL,
    status      TEXT NOT NULL DEFAULT 'DRAFT'
                    CHECK (status IN ('DRAFT','SENT','PAID','OVERDUE','CANCELLED')),
    total_paise BIGINT NOT NULL CHECK (total_paise > 0),
    due_date    DATE NOT NULL,
    issue_date  DATE NOT NULL DEFAULT CURRENT_DATE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (business_id, number)
);
CREATE INDEX idx_invoices_biz_status_due  ON invoices (business_id, status, due_date);
CREATE INDEX idx_invoices_biz_customer    ON invoices (business_id, customer_id);
CREATE INDEX idx_invoices_biz_created     ON invoices (business_id, created_at);

-- Invoice line items. line_total is derived; the server always recomputes the
-- invoice total, the client never sends it.
CREATE TABLE invoice_items (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id       UUID NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    description      TEXT NOT NULL,
    qty              INT NOT NULL CHECK (qty > 0),
    unit_price_paise BIGINT NOT NULL CHECK (unit_price_paise >= 0)
);
CREATE INDEX idx_invoice_items_invoice ON invoice_items (invoice_id);

-- Payments are separate from invoice status.
-- idempotency_key prevents double "mark as paid".
CREATE TABLE payments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id       UUID NOT NULL REFERENCES invoices(id),
    business_id     UUID NOT NULL REFERENCES businesses(id),
    amount_paise     BIGINT NOT NULL CHECK (amount_paise > 0),
    method           TEXT NOT NULL,
    idempotency_key  TEXT UNIQUE,
    paid_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_payments_invoice  ON payments (invoice_id);
CREATE INDEX idx_payments_business ON payments (business_id);

-- Audit log for state-machine transitions
CREATE TABLE audit_log (
    id             BIGSERIAL PRIMARY KEY,
    business_id    UUID NOT NULL,
    entity         TEXT NOT NULL,
    entity_id      UUID NOT NULL,
    from_state     TEXT,
    to_state       TEXT,
    actor_user_id  UUID,
    at             TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_log_entity ON audit_log (entity, entity_id);
