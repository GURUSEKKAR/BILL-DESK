package com.invoice.modules.customers.controller;

import com.invoice.modules.customers.dto.*;
import com.invoice.modules.customers.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @PostMapping
    public CustomerResponse create(@Valid @RequestBody CreateCustomerRequest request) {
        return customerService.create(request);
    }

    @GetMapping
    public Page<CustomerResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search) {
        return customerService.list(
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")),
            search);
    }

    @GetMapping("/{id}")
    public CustomerResponse get(@PathVariable UUID id) {
        return customerService.get(id);
    }

    /**
     * Business-wide balance + credit status. Available to OWNER and CASHIER.
     * Returns no invoice details, so a cashier cannot see other counters' bills.
     */
    @GetMapping("/{id}/balance")
    public com.invoice.modules.customers.dto.CustomerBalanceResponse balance(@PathVariable UUID id) {
        return customerService.balance(id);
    }

    @PutMapping("/{id}")
    public CustomerResponse update(@PathVariable UUID id,
                                   @Valid @RequestBody UpdateCustomerRequest request) {
        return customerService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        customerService.delete(id);
    }
}
