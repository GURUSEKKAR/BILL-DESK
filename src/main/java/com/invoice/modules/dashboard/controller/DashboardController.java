package com.invoice.modules.dashboard.controller;

import com.invoice.modules.dashboard.dto.ByCounterResponse;
import com.invoice.modules.dashboard.dto.DashboardSummary;
import com.invoice.modules.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * Scope-aware summary. OWNER may filter with {@code ?counterId=}; a CASHIER
     * always gets their own counter (the param is ignored by ScopeResolver).
     */
    @GetMapping("/summary")
    public DashboardSummary summary(@RequestParam(required = false) UUID counterId) {
        return dashboardService.summary(counterId);
    }

    /** Owner-only per-counter breakdown. */
    @GetMapping("/by-counter")
    @PreAuthorize("hasRole('OWNER')")
    public ByCounterResponse byCounter() {
        return dashboardService.byCounter();
    }
}
