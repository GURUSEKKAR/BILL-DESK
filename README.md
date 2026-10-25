# Invoice Platform

Multi-tenant invoicing ledger with an async reminder engine and a
multi-counter (OWNER / CASHIER) hierarchy.

- **Backend:** Spring Boot 4 / Java 21, PostgreSQL, Flyway, JWT auth.
- **Frontend:** React + Vite + TypeScript + TailwindCSS.
- **Worker:** in-process Postgres-backed job queue (invoice email + reminders).

## Running locally

```bash
docker compose up -d postgres        # Postgres on :5432
cp .env.example .env                 # set RESEND_API_KEY etc. (optional)
mvn spring-boot:run                  # API + worker on :8080
cd frontend && npm install && npm run dev   # UI on :5173
```

Without a `RESEND_API_KEY`, email is delivered over SMTP
(`spring.mail.*`) — used by local dev and the integration tests (GreenMail).
PDF generation was removed intentionally (it was resource-heavy); invoices are
delivered to customers by email only.

## Two ways to sign in

- **Admin (owner):** email + admin password → sees everything.
- **Counter (cashier):** **business code** + **counter code** + **counter
  password** → scoped to that one counter.

The **business code** is chosen by the owner at registration (or auto-generated)
and is the same for every counter in the business. Each counter has its own
code (C1, C2, …) and its own password, set by the owner on the Counters page.

## Tests

Integration tests use Testcontainers / a local Postgres plus GreenMail. Run:

```bash
docker compose up -d postgres
mvn test
```

---

## Phase 3 — multi-counter hierarchy (lite)

One business, many **counters**. The OWNER sees everything; a CASHIER sees
only their own counter. Customers stay shared across the business; reminders
stay business-level.

### Roles & scope

- `OWNER` — business-wide. Can manage counters and users, create invoices on
  any counter, and see a per-counter dashboard.
- `CASHIER` — pinned to one counter. Only ever sees/acts on that counter.

Scope is resolved in exactly one place, `ScopeResolver`:

- **CASHIER** → always their own counter; a requested `?counterId=` is ignored.
- **OWNER** → the requested `counterId` (validated to belong to the business),
  otherwise business-wide (`null`).

JWTs carry `role` and (for cashiers) `counterId`. Sensitive actions re-load the
user from the DB so a stale token (reassigned / deactivated cashier) loses
access.

### Endpoints

| Endpoint | Who |
| --- | --- |
| `POST /auth/register` (optional custom `businessCode`) | public |
| `POST /auth/login` (admin email + password) | public |
| `POST /auth/counter-login` (business code + counter code + counter password) | public |
| `GET/PATCH /business` (name + login code) | OWNER |
| `POST/GET/GET{id}/PATCH{id} /counters` (incl. counter password) | OWNER |
| `POST /users` (create cashier with `counterId`) | OWNER |
| `GET /users`, `PATCH /users/{id}` (reassign / deactivate) | OWNER |
| `GET /dashboard/summary?counterId=` | OWNER (optional filter), CASHIER (own, param ignored) |
| `GET /dashboard/by-counter` (per-counter sales, collected, outstanding, overdue, customer count) | OWNER only |
| `GET /customers/{id}/balance` | Both (business-wide balance, no invoice details) |

Existing invoice/payment URLs are unchanged — only their behaviour changes
through scope. An OWNER creating an invoice must pass an explicit `counterId`;
a CASHIER's counter is taken from their assignment.

### Per-counter invoice numbers

Numbers are `<counterCode>-<year>-<seq>`, e.g. `C1-2026-000001`, with an
independent sequence per `(business, counter, year)` generated atomically via
`INSERT ... ON CONFLICT ... RETURNING`.

### Phase 3b — credit limit

A customer may have a `credit_limit_paise`. Sending a credit invoice locks the
customer row (`SELECT ... FOR UPDATE`, always **customer before invoice**),
computes `outstanding + new total`, and rejects with **409** if the limit would
be exceeded. The consistent lock order prevents deadlocks and serialises two
counters billing the same customer in parallel.

### Dashboards

"Today" is an **IST** calendar day (`Asia/Kolkata`; midnight IST == 18:30 UTC),
computed from an injectable `Clock` bean so it's testable around the boundary.

---

## Roadmap (explicitly out of scope for Phase 3)

These were deferred to keep Phase 3 focused:

- `MANAGER` role, shifts, day-close.
- Voids and refunds with approval.
- Inventory, barcode, thermal printing.
- PostgreSQL Row-Level Security (defence-in-depth stretch goal).
- Offline sync.
