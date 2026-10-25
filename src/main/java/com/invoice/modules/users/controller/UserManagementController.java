package com.invoice.modules.users.controller;

import com.invoice.modules.users.dto.*;
import com.invoice.modules.users.service.UserManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Cashier account management — OWNER only.
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserManagementController {

    private final UserManagementService userManagementService;

    @PostMapping
    @PreAuthorize("hasRole('OWNER')")
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return userManagementService.createCashier(request);
    }

    @GetMapping
    @PreAuthorize("hasRole('OWNER')")
    public List<UserResponse> list() {
        return userManagementService.list();
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public UserResponse update(@PathVariable UUID id,
                               @Valid @RequestBody UpdateUserRequest request) {
        return userManagementService.update(id, request);
    }
}
