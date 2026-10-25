package com.invoice.modules.dashboard.service;

import com.invoice.common.exception.ApiException;
import com.invoice.modules.counters.entity.Counter;
import com.invoice.modules.counters.repository.CounterRepository;
import com.invoice.modules.dashboard.dto.ByCounterResponse;
import com.invoice.modules.dashboard.dto.CounterSummary;
import com.invoice.modules.dashboard.dto.CustomerOutstanding;
import com.invoice.modules.dashboard.dto.DashboardSummary;
import com.invoice.modules.invoices.InvoiceStatus;
import com.invoice.modules.invoices.repository.InvoiceRepository;
import com.invoice.modules.payments.repository.PaymentRepository;
import com.invoice.modules.auth.entity.Role;
import com.invoice.security.AuthContext;
import com.invoice.security.Scope;
import com.invoice.security.ScopeResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final CounterRepository counterRepository;
    private final ScopeResolver scopeResolver;
    private final Clock clock;

    /** Day boundaries are defined in IST regardless of server timezone. */
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private static final List<InvoiceStatus> OUTSTANDING_STATUSES =
        List.of(InvoiceStatus.SENT, InvoiceStatus.OVERDUE);

    /**
     * Summary for the caller's scope.
     * OWNER: all counters, or one when {@code requestedCounterId} is given.
     * CASHIER: own counter (requestedCounterId ignored).
     */
    public DashboardSummary summary(UUID requestedCounterId) {
        Scope scope = scopeResolver.resolve(requestedCounterId);
        UUID businessId = scope.businessId();
        UUID counterId = scope.counterId(); // null = all

        Long outstanding = invoiceRepository.sumByStatuses(businessId, counterId, OUTSTANDING_STATUSES);
        Long overdue = invoiceRepository.sumByStatuses(businessId, counterId, List.of(InvoiceStatus.OVERDUE));
        Long paid = invoiceRepository.sumByStatuses(businessId, counterId, List.of(InvoiceStatus.PAID));
        Long invoiceCount = invoiceRepository.countByStatuses(businessId, counterId, OUTSTANDING_STATUSES);
        Long overdueCount = invoiceRepository.countByStatuses(businessId, counterId, List.of(InvoiceStatus.OVERDUE));
        List<CustomerOutstanding> perCustomer =
            invoiceRepository.outstandingPerCustomer(businessId, counterId, OUTSTANDING_STATUSES);

        Instant[] today = todayWindow();
        long collectedToday = nz(paymentRepository.collectedBetween(businessId, counterId, today[0], today[1]));
        Map<String, Long> byMethod = methodMap(
            paymentRepository.collectedByMethodBetween(businessId, counterId, today[0], today[1]));

        LocalDate day = LocalDate.now(clock.withZone(IST));
        long salesToday = counterId != null
            ? nz(invoiceRepository.sumByCounterStatusesOnDay(businessId, counterId, ALL_LIVE, day))
            : sumAcrossCounters(businessId, ALL_LIVE, day);
        long creditGivenToday = counterId != null
            ? nz(invoiceRepository.sumByCounterStatusesOnDay(businessId, counterId, OUTSTANDING_STATUSES, day))
            : sumAcrossCounters(businessId, OUTSTANDING_STATUSES, day);

        return new DashboardSummary(
            nz(outstanding), nz(overdue), nz(paid),
            nz(invoiceCount), nz(overdueCount),
            salesToday, collectedToday, creditGivenToday, byMethod,
            perCustomer);
    }

    /** OWNER-only: per-counter table + top debtors across the whole business. */
    public ByCounterResponse byCounter() {
        if (AuthContext.role() != Role.OWNER) {
            throw ApiException.notFound("Resource"); // method security also guards this
        }
        UUID businessId = AuthContext.businessId();
        Instant[] today = todayWindow();
        LocalDate day = LocalDate.now(clock.withZone(IST));

        List<Counter> counters = counterRepository.findByBusinessIdOrderByCodeAsc(businessId);
        List<CounterSummary> rows = new ArrayList<>();
        for (Counter c : counters) {
            UUID cid = c.getId();
            long sales = nz(invoiceRepository.sumByCounterStatusesOnDay(businessId, cid, ALL_LIVE, day));
            long collected = nz(paymentRepository.collectedBetween(businessId, cid, today[0], today[1]));
            long credit = nz(invoiceRepository.sumByCounterStatusesOnDay(businessId, cid, OUTSTANDING_STATUSES, day));
            Map<String, Long> byMethod = methodMap(
                paymentRepository.collectedByMethodBetween(businessId, cid, today[0], today[1]));
            long outstanding = nz(invoiceRepository.sumByStatuses(businessId, cid, OUTSTANDING_STATUSES));
            long overdue = nz(invoiceRepository.sumByStatuses(businessId, cid, List.of(InvoiceStatus.OVERDUE)));
            long customerCount = nz(invoiceRepository.distinctCustomerCount(businessId, cid));
            rows.add(new CounterSummary(cid, c.getCode(), c.getName(),
                sales, collected, credit, byMethod, outstanding, overdue, customerCount));
        }

        List<CustomerOutstanding> topDebtors =
            invoiceRepository.outstandingPerCustomer(businessId, null, OUTSTANDING_STATUSES);

        return new ByCounterResponse(rows, topDebtors);
    }

    // "Live" (non-cancelled, non-draft) invoices count as a sale once issued.
    private static final List<InvoiceStatus> ALL_LIVE =
        List.of(InvoiceStatus.SENT, InvoiceStatus.OVERDUE, InvoiceStatus.PAID);

    private long sumAcrossCounters(UUID businessId, List<InvoiceStatus> statuses, LocalDate day) {
        long sum = 0;
        for (Counter c : counterRepository.findByBusinessIdOrderByCodeAsc(businessId)) {
            sum += nz(invoiceRepository.sumByCounterStatusesOnDay(businessId, c.getId(), statuses, day));
        }
        return sum;
    }

    /** [startOfTodayUTC, startOfTomorrowUTC) for the IST calendar day. */
    private Instant[] todayWindow() {
        LocalDate today = LocalDate.now(clock.withZone(IST));
        Instant from = today.atStartOfDay(IST).toInstant();
        Instant to = today.plusDays(1).atStartOfDay(IST).toInstant();
        return new Instant[]{from, to};
    }

    private static Map<String, Long> methodMap(List<Object[]> rows) {
        Map<String, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((String) row[0], ((Number) row[1]).longValue());
        }
        return map;
    }

    private static long nz(Long v) {
        return v != null ? v : 0;
    }
}
