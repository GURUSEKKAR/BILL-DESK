-- ============================================================================
-- Phase 2 schema: jobs (queue), reminders, payment_links, pdf_path
-- ============================================================================

-- Job queue (Postgres-backed, uses SKIP LOCKED for concurrent claiming)
CREATE TABLE jobs (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type          TEXT NOT NULL,
    payload       JSONB NOT NULL DEFAULT '{}'::jsonb,
    status        TEXT NOT NULL DEFAULT 'PENDING'
                       CHECK (status IN ('PENDING','SENDING','DONE','FAILED')),
    attempts      INT  NOT NULL DEFAULT 0,
    max_attempts  INT  NOT NULL DEFAULT 5,
    next_run_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_error    TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_jobs_status_next_run ON jobs (status, next_run_at);
CREATE INDEX idx_jobs_type_status      ON jobs (type, status);

-- Reminders (5 per invoice: PRE_DUE, ON_DUE, POST_3, POST_7, POST_14)
CREATE TABLE reminders (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id   UUID NOT NULL REFERENCES invoices(id),
    business_id  UUID NOT NULL REFERENCES businesses(id),
    type         TEXT NOT NULL CHECK (type IN ('PRE_DUE','ON_DUE','POST_3','POST_7','POST_14')),
    status       TEXT NOT NULL DEFAULT 'PENDING'
                      CHECK (status IN ('PENDING','SENDING','SENT','CANCELLED','FAILED')),
    scheduled_at TIMESTAMPTZ NOT NULL,
    sent_at      TIMESTAMPTZ,
    last_error   TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (invoice_id, type)
);
CREATE INDEX idx_reminders_status_scheduled ON reminders (status, scheduled_at);

-- Payment links (signed token URLs for the mock payment page)
CREATE TABLE payment_links (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id  UUID NOT NULL REFERENCES invoices(id),
    business_id UUID NOT NULL REFERENCES businesses(id),
    token       TEXT NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_payment_links_token ON payment_links (token);

-- PDF path on invoices
ALTER TABLE invoices ADD COLUMN pdf_path TEXT;
