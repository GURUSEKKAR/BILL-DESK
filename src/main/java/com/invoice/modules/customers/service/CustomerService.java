package com.invoice.modules.customers.service;

import com.invoice.common.exception.ApiException;
import com.invoice.modules.customers.dto.*;
import com.invoice.modules.customers.entity.Customer;
import com.invoice.modules.customers.repository.CustomerRepository;
import com.invoice.modules.invoices.InvoiceStatus;
import com.invoice.modules.invoices.repository.InvoiceRepository;
import com.invoice.security.AuthContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final InvoiceRepository invoiceRepository;

    private static final List<InvoiceStatus> OUTSTANDING_STATUSES =
        List.of(InvoiceStatus.SENT, InvoiceStatus.OVERDUE);

    @Transactional
    public CustomerResponse create(CreateCustomerRequest request) {
        Customer customer = new Customer();
        customer.setBusinessId(AuthContext.businessId());
        customer.setName(request.name());
        customer.setEmail(request.email());
        customer.setPhone(request.phone());
        customer.setCreditLimitPaise(request.creditLimitPaise());
        customer = customerRepository.save(customer);
        return toResponse(customer);
    }

    public Page<CustomerResponse> list(Pageable pageable, String search) {
        UUID businessId = AuthContext.businessId();
        Page<Customer> page;
        if (search != null && !search.isBlank()) {
            page = customerRepository.searchByName(businessId, search.trim(), pageable);
        } else {
            page = customerRepository.findByBusinessIdOrderByIdDesc(businessId, pageable);
        }
        return page.map(this::toResponse);
    }

    public CustomerResponse get(UUID id) {
        Customer customer = customerRepository
            .findByIdAndBusinessId(id, AuthContext.businessId())
            .orElseThrow(() -> ApiException.notFound("Customer"));
        return toResponse(customer);
    }

    /** Used internally by the invoice service — returns the entity, not a DTO. */
    public Customer getOwned(UUID id, UUID businessId) {
        return customerRepository
            .findByIdAndBusinessId(id, businessId)
            .orElseThrow(() -> ApiException.notFound("Customer"));
    }

    @Transactional
    public CustomerResponse update(UUID id, UpdateCustomerRequest request) {
        Customer customer = customerRepository
            .findByIdAndBusinessId(id, AuthContext.businessId())
            .orElseThrow(() -> ApiException.notFound("Customer"));
        if (request.name() != null) customer.setName(request.name());
        if (request.email() != null) customer.setEmail(request.email());
        if (request.phone() != null) customer.setPhone(request.phone());
        if (request.creditLimitPaise() != null) customer.setCreditLimitPaise(request.creditLimitPaise());
        customer = customerRepository.save(customer);
        return toResponse(customer);
    }

    @Transactional
    public void delete(UUID id) {
        Customer customer = customerRepository
            .findByIdAndBusinessId(id, AuthContext.businessId())
            .orElseThrow(() -> ApiException.notFound("Customer"));
        customerRepository.delete(customer);
    }

    /**
     * Business-wide balance for a customer. Available to BOTH roles — a cashier
     * can see what a customer owes across the whole business and their credit
     * status, WITHOUT seeing invoices raised at other counters.
     */
    public CustomerBalanceResponse balance(UUID id) {
        UUID businessId = AuthContext.businessId();
        Customer customer = customerRepository.findByIdAndBusinessId(id, businessId)
            .orElseThrow(() -> ApiException.notFound("Customer"));

        long outstanding = nz(invoiceRepository.outstandingForCustomer(
            businessId, id, OUTSTANDING_STATUSES));
        Long limit = customer.getCreditLimitPaise();
        Long available = limit != null ? Math.max(0, limit - outstanding) : null;

        return new CustomerBalanceResponse(
            customer.getId(), customer.getName(), outstanding, limit, available);
    }

    private static long nz(Long v) {
        return v != null ? v : 0;
    }

    private CustomerResponse toResponse(Customer c) {
        return new CustomerResponse(c.getId(), c.getName(), c.getEmail(),
            c.getPhone(), c.getCreditLimitPaise(), c.getCreatedAt());
    }
}
