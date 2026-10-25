# Architecture Decisions

Key decisions and the reasoning behind them. These become interview talking points.

## Why paise (integer cents) instead of float/double?

Floating-point arithmetic introduces rounding errors: `0.1 + 0.2 = 0.30000000000000004`.
For financial data this is unacceptable. Storing money as integer paise (1 rupee = 100 paise)
guaranteed exact arithmetic. The `total_paise` column is `BIGINT`, so the range is more
than sufficient. All monetary values flow through the system as `long` paise and are only
converted to display format at the API boundary.

## Why SELECT ... FOR UPDATE on state transitions?

Two concurrent requests could try to transition the same invoice simultaneously
(e.g., a payment and a cancel request at the same time). Without a lock, both would
read the same current status, both would validate successfully, and one would overwrite
the other's change. `SELECT ... FOR UPDATE` acquires a row-level lock so the second
request blocks until the first commits. The Hibernate `@Lock(LockModeType.PESSIMISTIC_WRITE)`
annotation generates this lock automatically.

## Why a separate payments table instead of a boolean "is_paid" flag?

A payment is a record with an amount, method, timestamp, and idempotency key.
Collapsing it into a boolean loses all of that. Keeping payments separate also means
partial payments, multiple payment attempts, and audit trails are all possible without
schema changes.

## Why idempotency keys on payments?

Network failures cause clients to retry. Without protection, a retried "mark as paid"
request would create a duplicate payment. The `Idempotency-Key` header + a `UNIQUE`
constraint on the `idempotency_key` column guarantees that the same key always returns
the same payment, even under concurrent retries. The handling covers three race
windows: fast path (key already exists), slow path (invoice already PAID, find by key),
and unique-constraint violation (catch and fetch).

## Why business_id from the JWT, never from the request body?

If the client could specify `business_id` in the request body or URL, a malicious user
could read or modify another tenant's data. By embedding `business_id` in the signed
JWT and reading it via `AuthContext.businessId()` in every service, cross-tenant access
is impossible by construction. Every repository query includes `WHERE business_id = ?`
from this source.

## Why cursor pagination instead of offset pagination?

With `OFFSET 100000 LIMIT 20`, PostgreSQL must scan and discard 100,000 rows.
Cursor pagination uses `WHERE (created_at, id) < (?, ?)` which leverages the composite
index and skips directly to the right position. The cursor is opaque (base64-encoded)
so clients can't tamper with it.

## Why invoice number via INSERT ... ON CONFLICT ... RETURNING?

A simple `SELECT MAX(seq) + 1` has a race condition: two concurrent invoices get the
same number. PostgreSQL's `INSERT ... ON CONFLICT DO UPDATE ... RETURNING` is atomic:
the first call inserts a row, subsequent calls increment and return the new value, all
under the protection of the unique constraint. No explicit lock needed.

## Why one state-machine validation function?

All transitions flow through `InvoiceStateMachine.validate()`. This means the rules
are defined in exactly one place and can never be bypassed, whether the transition
comes from a user action (send, cancel), a payment, or the system (overdue job).
Changing a rule means changing one method.

## Why each overdue invoice in its own transaction?

If one invoice fails to transition (e.g., it was already paid by a concurrent request),
that shouldn't roll back the others. Each `markOverdue` call opens its own transaction
with `@Transactional`, so failures are isolated.

## Phase 2 preview: at-least-once delivery with idempotency

The async reminder engine (Phase 2) will use at-least-once delivery: a reminder job
might be delivered more than once if the worker crashes. This is safe because the
email provider call is idempotent (idempotency key), and the reminder row has a
`UNIQUE(invoice_id, type)` constraint so duplicate sends are caught.

## Phase 3: why one central ScopeResolver instead of per-query filtering?

The single biggest risk in a multi-counter system is forgetting the counter
filter on one query and leaking another counter's data. Centralising the
"who is calling + what did they ask for → effective scope" decision in
`ScopeResolver` means the rule lives in one place: CASHIER is always pinned to
their own counter (a requested counterId is ignored, not an error), and OWNER
gets either a validated counter or business-wide. Repositories then apply a
uniform `WHERE business_id = ? AND (:counterId IS NULL OR counter_id = ?)`
pattern. A null counterId means "all counters" and only an OWNER can hold it.

## Phase 3: why counter scope from the JWT, re-validated on sensitive actions?

Like `business_id`, `role` and `counter_id` live in the signed JWT and are read
via `AuthContext` — never from the request body. But unlike `business_id`, a
cashier's role/counter can change (reassignment, deactivation) while an old
access token is still valid. So sensitive actions (invoice/payment creation,
and `refresh`) re-load the user row and reject if the user is inactive or their
role/counter no longer matches the token. Access tokens stay short-lived (15m)
to bound the staleness window.

## Phase 3: why per-(business, counter, year) invoice numbers?

Each counter wants its own clean sequence (C1-2026-000001, C2-2026-000001).
Re-keying `invoice_number_seq` to `(business_id, counter_id, year)` keeps the
same atomic `INSERT ... ON CONFLICT DO UPDATE ... RETURNING` guarantee per
counter, so 50 invoices created concurrently on two counters yield unique,
gap-tolerant, per-counter sequences with no explicit locking.

## Phase 3: why migrate existing data nullable → backfill → NOT NULL?

V4 runs on live V1/V2/V3 data. New required columns (`invoices.counter_id`,
`payments.counter_id`, `users.business_id/role`) are added nullable first, every
existing row is backfilled onto a seeded default "Main" (C1) counter / OWNER
role, and only then are the NOT NULL constraints applied. `created_by` /
`received_by` stay nullable because historical rows have no reliable actor.

## Phase 3: why DEFERRABLE FKs between users and businesses?

A user points at its business and a business points at its owner user — a
mutual dependency. Making both FKs `DEFERRABLE INITIALLY DEFERRED` lets
registration insert the owner+business pair in one transaction regardless of
statement order; the constraints are checked at commit, by which point both
rows exist.

## Phase 3b: why lock the customer before the invoice, every time?

The credit-limit check reads outstanding + the new invoice total and compares
it with the limit. Two counters billing the same customer concurrently could
both read a stale "under limit" and both proceed. Locking the customer row
(`SELECT ... FOR UPDATE`) serialises them so exactly one passes when the limit
allows only one. Always acquiring the customer lock *before* the invoice lock
gives a consistent global lock order, which prevents deadlocks.

## Phase 3: why an IST Clock bean for "today"?

Daily figures (sales/collected/credit today) are defined on the IST calendar
day, but the server and the database store timestamps in UTC — and midnight IST
is 18:30 UTC. Computing the day window from an injectable `Clock` fixed to
`Asia/Kolkata` makes the boundary explicit and lets a test pin the clock just
after IST midnight to prove payments are bucketed into the right day.
