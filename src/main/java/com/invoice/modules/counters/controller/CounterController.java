package com.invoice.modules.counters.controller;

import com.invoice.modules.counters.dto.*;
import com.invoice.modules.counters.service.CounterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Counter management — OWNER only (enforced with method security).
 */
@RestController
@RequestMapping("/counters")
@RequiredArgsConstructor
public class CounterController {

    private final CounterService counterService;

    @PostMapping
    @PreAuthorize("hasRole('OWNER')")
    public CounterResponse create(@Valid @RequestBody CreateCounterRequest request) {
        return counterService.create(request);
    }

    @GetMapping
    @PreAuthorize("hasRole('OWNER')")
    public List<CounterResponse> list() {
        return counterService.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public CounterResponse get(@PathVariable UUID id) {
        return counterService.get(id);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public CounterResponse update(@PathVariable UUID id,
                                  @Valid @RequestBody UpdateCounterRequest request) {
        return counterService.update(id, request);
    }
}
