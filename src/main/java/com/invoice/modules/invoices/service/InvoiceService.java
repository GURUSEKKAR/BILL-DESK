package com.invoice.modules.invoices.service;

import com.invoice.common.CursorUtil;
import com.invoice.common.PageResult;
import com.invoice.common.audit.AuditLogService;
import com.invoice.common.exception.ApiException;
import com.invoice.modules.customers.service.CustomerService;
import com.invoice.modules.invoices.InvoiceStateMachine;
import com.invoice.modules.invoices.InvoiceStatus;
import com.invoice.modules.invoices.dto.*;
import com.invoice.modules.invoices.entity.Invoice;
import com.invoice.modules.invoices.entity.InvoiceItem;
import com.invoice.modules.invoices.repository.InvoiceRepository;
import com.invoice.modules.jobs.service.JobService;
import com.invoice.modules.reminders.service.ReminderService;
import com.invoice.security.AuthContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceNumberGenerator invoiceNumberGenerator;
    private final CustomerService customerService;
    private final AuditLogService auditLogService;
    private final JobService jobService;
    private final ReminderService reminderService;
    private final com.invoice.security.ScopeResolver scopeResolver;
    private final com.invoice.modules.counters.repository.CounterRepository counterRepository;
    private final com.invoice.modules.customers.repository.CustomerRepository customerRepository;

    @PersistenceContext
    private EntityManager entityManager;

    // -------------------------------------------------------------------------
    // Create
    // -------------------------------------------------------------------------

    @Transactional
    public InvoiceResponse create(CreateInvoiceRequest request) {
        // Resolve the target counter via the single scope chokepoint.
        // CASHIER -> their own counter; OWNER -> explicit counterId (required).
        com.invoice.security.Scope scope = scopeResolver.resolveForWrite(request.counterId());
        UUID businessId = scope.businessId();
        UUID counterId = scope.counterId();

        // Verify the customer belongs to this business (prevents cross-tenant ref)
        customerService.getOwned(request.customerId(), businessId);

        // Resolve the counter code for the human-readable number (e.g. C1-2026-000001)
        var counter = counterRepository.findByIdAndBusinessId(counterId, businessId)
            .orElseThrow(() -> ApiException.notFound("Counter"));

        // Generate a unique invoice number per business per counter per year
        int year = Year.now().getValue();
        int seq = invoiceNumberGenerator.nextNumber(businessId, counterId, year);
        String number = String.format("%s-%d-%06d", counter.getCode(), year, seq);

        // Build the invoice; the server computes the total, never the client
        Invoice invoice = new Invoice();
        invoice.setBusinessId(businessId);
        invoice.setCounterId(counterId);
        invoice.setCreatedBy(AuthContext.userId());
        invoice.setCustomerId(request.customerId());
        invoice.setNumber(number);
        invoice.setStatus(InvoiceStatus.DRAFT);
        invoice.setDueDate(request.dueDate());
        invoice.setIssueDate(LocalDate.now());

        long total = 0;
        for (InvoiceItemRequest dto : request.items()) {
            InvoiceItem item = new InvoiceItem();
            item.setDescription(dto.description());
            item.setQty(dto.qty());
            item.setUnitPricePaise(dto.unitPricePaise());
            invoice.addItem(item);
            total += item.lineTotalPaise();
        }
        if (total <= 0) {
            throw ApiException.badRequest("Invoice total must be greater than zero");
        }
        invoice.setTotalPaise(total);

        invoice = invoiceRepository.save(invoice);
        return toResponse(invoice);
    }

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    public InvoiceResponse getById(UUID id) {
        // Scope: OWNER (counter null) sees any counter; CASHIER only their own.
        Invoice invoice = invoiceRepository
            .findWithItemsScoped(id, AuthContext.businessId(), AuthContext.counterId())
            .orElseThrow(() -> ApiException.notFound("Invoice"));
        return toResponse(invoice);
    }

    /**
     * Cursor-paginated list with filters.
     * Uses a native query to avoid Hibernate 7 JpaSpecificationExecutor issues.
     *
     * @param requestedCounterId optional OWNER filter; ignored for a CASHIER.
     */
    public PageResult<InvoiceListItemResponse> list(
            InvoiceStatus status, UUID customerId, UUID requestedCounterId,
            String cursor, int limit) {

        com.invoice.security.Scope scope = scopeResolver.resolve(requestedCounterId);
        UUID businessId = scope.businessId();
        UUID counterId = scope.counterId(); // null = all counters (owner)

        Instant cursorCreatedAt = null;
        if (cursor != null && !cursor.isBlank()) {
            try {
                String[] parts = CursorUtil.decode(cursor);
                cursorCreatedAt = Instant.parse(parts[0]);
            } catch (Exception e) {
                log.warn("Invalid cursor ignored: {}", cursor);
            }
        }

        // Build query via native SQL to avoid Specification crashes in Hibernate 7
        StringBuilder sql = new StringBuilder(
            "SELECT i FROM Invoice i WHERE i.businessId = :businessId");
        if (counterId != null) {
            sql.append(" AND i.counterId = :counterId");
        }
        if (status != null) {
            sql.append(" AND i.status = :status");
        }
        if (customerId != null) {
            sql.append(" AND i.customerId = :customerId");
        }
        if (cursorCreatedAt != null) {
            sql.append(" AND i.createdAt < :cursorCreatedAt");
        }
        sql.append(" ORDER BY i.createdAt DESC, i.id DESC");

        var query = entityManager.createQuery(sql.toString(), Invoice.class);
        query.setParameter("businessId", businessId);
        if (counterId != null) query.setParameter("counterId", counterId);
        if (status != null) query.setParameter("status", status);
        if (customerId != null) query.setParameter("customerId", customerId);
        if (cursorCreatedAt != null) query.setParameter("cursorCreatedAt", cursorCreatedAt);
        query.setMaxResults(limit + 1);

        List<Invoice> invoices = query.getResultList();

        boolean hasMore = invoices.size() > limit;
        List<Invoice> page = hasMore ? invoices.subList(0, limit) : invoices;

        String nextCursor = null;
        if (hasMore) {
            Invoice last = page.get(page.size() - 1);
            nextCursor = CursorUtil.encode(last.getCreatedAt().toString(),
                                           last.getId().toString());
        }

        return PageResult.of(
            page.stream().map(this::toListItemResponse).toList(),
            nextCursor
        );
    }

    // -------------------------------------------------------------------------
    // State transitions (user-initiated)
    // -------------------------------------------------------------------------

    @Transactional
    public InvoiceResponse send(UUID invoiceId) {
        UUID businessId = AuthContext.businessId();

        // Phase 3b: credit-limit check with locking.
        // Lock the CUSTOMER first (consistent lock order -> customer before invoice)
        // so two counters billing the same customer in parallel are serialized.
        // Peek the invoice (no lock) just to find the customer + amount.
        Invoice peek = invoiceRepository.findByIdAndBusinessId(invoiceId, businessId)
            .orElseThrow(() -> ApiException.notFound("Invoice"));
        var customer = customerRepository.findByIdForUpdate(peek.getCustomerId(), businessId)
            .orElseThrow(() -> ApiException.notFound("Customer"));
        if (customer.getCreditLimitPaise() != null) {
            long outstanding = nz(invoiceRepository.outstandingForCustomer(
                businessId, customer.getId(), CREDIT_STATUSES));
            long projected = outstanding + peek.getTotalPaise();
            if (projected > customer.getCreditLimitPaise()) {
                throw new ApiException(org.springframework.http.HttpStatus.CONFLICT,
                    "Credit limit exceeded: outstanding " + outstanding
                        + " + invoice " + peek.getTotalPaise()
                        + " > limit " + customer.getCreditLimitPaise());
            }
        }

        Invoice invoice = transition(invoiceId, businessId, InvoiceStatus.SENT);

        // Schedule the 5 reminder rows (idempotent — UNIQUE constraint protects duplicates)
        reminderService.scheduleReminders(invoiceId, businessId, invoice.getDueDate());

        // Enqueue the invoice email job (PDF generation removed entirely).
        jobService.enqueue("send_invoice_email", Map.of("invoiceId", invoiceId.toString()));

        return toResponse(invoice);
    }

    private static final List<InvoiceStatus> CREDIT_STATUSES =
        List.of(InvoiceStatus.SENT, InvoiceStatus.OVERDUE);

    private static long nz(Long v) {
        return v != null ? v : 0;
    }

    @Transactional
    public InvoiceResponse cancel(UUID invoiceId) {
        return toResponse(transition(invoiceId, AuthContext.businessId(), InvoiceStatus.CANCELLED));
    }

    /**
     * The core transition method: locks the row, validates, updates, and
     * writes the audit log — all in one transaction.
     */
    @Transactional
    public Invoice transition(UUID invoiceId, UUID businessId, InvoiceStatus to) {
        Invoice invoice = invoiceRepository
            .findByIdForUpdate(invoiceId, businessId)
            .orElseThrow(() -> ApiException.notFound("Invoice"));

        // Scope guard: a CASHIER may only act on their own counter's invoices.
        // An OWNER (counterId null) may act on any counter.
        UUID callerCounter = AuthContext.counterId();
        if (callerCounter != null && !callerCounter.equals(invoice.getCounterId())) {
            throw ApiException.notFound("Invoice");
        }

        InvoiceStatus from = invoice.getStatus();
        InvoiceStateMachine.validate(from, to);   // single validation function
        invoice.setStatus(to);
        invoiceRepository.save(invoice);
        auditLogService.record(businessId, invoice.getCounterId(), "invoice",
            invoiceId, from.name(), to.name());
        return invoice;
    }

    // -------------------------------------------------------------------------
    // Internal helpers used by other services
    // -------------------------------------------------------------------------

    /** Returns a locked invoice entity for the caller's transaction. */
    @Transactional
    public Invoice getForUpdate(UUID invoiceId, UUID businessId) {
        return invoiceRepository
            .findByIdForUpdate(invoiceId, businessId)
            .orElseThrow(() -> ApiException.notFound("Invoice"));
    }

    /**
     * System-initiated transition (no authenticated user). Used by the
     * overdue job. Silently skips if the invoice is no longer SENT.
     */
    @Transactional
    public void markOverdue(UUID invoiceId, UUID businessId) {
        Invoice invoice = invoiceRepository
            .findByIdForUpdate(invoiceId, businessId)
            .orElseThrow(() -> ApiException.notFound("Invoice"));

        if (invoice.getStatus() != InvoiceStatus.SENT) {
            return; // already transitioned by a concurrent request
        }

        InvoiceStatus from = invoice.getStatus();
        InvoiceStateMachine.validate(from, InvoiceStatus.OVERDUE);
        invoice.setStatus(InvoiceStatus.OVERDUE);
        invoiceRepository.save(invoice);
        auditLogService.record(businessId, invoice.getCounterId(), "invoice", invoiceId,
            from.name(), InvoiceStatus.OVERDUE.name(), null);
    }

    // -------------------------------------------------------------------------
    // Mapping
    // -------------------------------------------------------------------------

    private InvoiceResponse toResponse(Invoice invoice) {
        List<InvoiceResponse.ItemResponse> items = invoice.getItems().stream()
            .map(it -> new InvoiceResponse.ItemResponse(
                it.getId(), it.getDescription(), it.getQty(),
                it.getUnitPricePaise(), it.lineTotalPaise()))
            .toList();
        return new InvoiceResponse(
            invoice.getId(), invoice.getNumber(), invoice.getStatus(),
            invoice.getTotalPaise(), invoice.getDueDate(), invoice.getIssueDate(),
            invoice.getCustomerId(), invoice.getCreatedAt(), items);
    }

    private InvoiceListItemResponse toListItemResponse(Invoice invoice) {
        return new InvoiceListItemResponse(
            invoice.getId(), invoice.getNumber(), invoice.getStatus(),
            invoice.getTotalPaise(), invoice.getDueDate(), invoice.getIssueDate(),
            invoice.getCustomerId(), invoice.getCreatedAt());
    }
}
