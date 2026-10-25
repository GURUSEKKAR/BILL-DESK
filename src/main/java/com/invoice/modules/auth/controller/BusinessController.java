package com.invoice.modules.auth.controller;

import com.invoice.modules.auth.dto.BusinessResponse;
import com.invoice.modules.auth.dto.UpdateBusinessRequest;
import com.invoice.modules.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Owner-only business settings — read and update the business name and the
 * login code that cashiers type at counter login.
 */
@RestController
@RequestMapping("/business")
@RequiredArgsConstructor
public class BusinessController {

    private final AuthService authService;

    @GetMapping
    @PreAuthorize("hasRole('OWNER')")
    public BusinessResponse get() {
        return authService.getBusiness();
    }

    @PatchMapping
    @PreAuthorize("hasRole('OWNER')")
    public BusinessResponse update(@Valid @RequestBody UpdateBusinessRequest request) {
        return authService.updateBusiness(request);
    }
}
