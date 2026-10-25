# Phase 3 scope checklist

Every repository method that touches tenant data, marked **scoped** (filters by
counter when the caller is a cashier / an owner filters) or
**business-level, intentionally** (shared across counters by design) or
**system** (runs outside a user context, all tenants).

The mitigation for "forgot the scope on one query" is this checklist plus the
"other counter" test on every endpoint in `CounterScopeTest`.

## invoices (`InvoiceRepository`)

| Method | Classification | Notes |
| --- | --- | --- |
| `findByIdForUpdate(id, businessId)` | business-level + app guard | Lock by business; `InvoiceService.transition`/`PaymentService` add a cashier counter guard (404 on foreign counter). |
| `findWithItemsByIdAndBusinessId` | legacy, unused by scoped reads | Superseded by `findWithItemsScoped`. |
| `findWithItemsScoped(id, businessId, counterId)` | **scoped** | Detail read; `counterId` null = owner (any), set = cashier (own). |
| `sumByStatuses(businessId, counterId, …)` | **scoped** | Dashboard; `counterId` null = all. |
| `countByStatuses(businessId, counterId, …)` | **scoped** | Dashboard. |
| `outstandingPerCustomer(businessId, counterId, …)` | **scoped** | Dashboard + top debtors (owner passes null). |
| `sumByCounterStatusesOnDay(businessId, counterId, …)` | **scoped** | Per-counter "today" figures. |
| `outstandingForCustomer(businessId, customerId, …)` | business-level, intentionally | Customer balance is business-wide by design (no counter filter, no invoice details returned). |
| `findByIdAndBusinessId(id, businessId)` | business-level + app guard | Used by payment-link (anonymous owner-wide) and credit-limit peek. |
| `findOverdueCandidates(status, date)` | system | Overdue job iterates all tenants; transitions re-lock per business/counter. |
| `list(...)` (JPQL in `InvoiceService`) | **scoped** | Adds `AND i.counterId = :counterId` when scope resolves to a counter. |

## payments (`PaymentRepository`)

| Method | Classification | Notes |
| --- | --- | --- |
| `findByIdempotencyKey(key)` | business-level, intentionally | Idempotency key is globally unique; `markAsPaid` re-checks the invoice's business + applies a cashier counter guard. |
| `collectedBetween(businessId, counterId, …)` | **scoped** | Dashboard "collected today". |
| `collectedByMethodBetween(businessId, counterId, …)` | **scoped** | Dashboard by-method. |

## customers (`CustomerRepository`)

| Method | Classification | Notes |
| --- | --- | --- |
| `findByIdAndBusinessId` | business-level, intentionally | Customers are shared across counters. |
| `findByIdForUpdate` | business-level, intentionally | Credit-limit lock; customers are business-wide. |
| `findByBusinessIdOrderByIdDesc` / `searchByName` | business-level, intentionally | Shared customer list. |

## counters (`CounterRepository`) — all OWNER-gated at the controller

| Method | Classification |
| --- | --- |
| `findByBusinessIdOrderByCodeAsc`, `findByIdAndBusinessId`, `findByBusinessIdAndCode`, `existsByBusinessIdAndCode`, `countByBusinessId` | business-level (counter admin) |

## users (`UserRepository`) — all OWNER-gated at the controller

| Method | Classification |
| --- | --- |
| `findByBusinessIdOrderByCreatedAtAsc`, `findByIdAndBusinessId` | business-level (cashier admin) |
| `findByEmail`, `existsByEmail`, `findById` | auth / scope re-validation |

## audit_log (`AuditLogRepository`)

Every write records `business_id`, `counter_id`, and `actor_user_id` via
`AuditLogService.record(...)` — answering "who did this, at which counter".
