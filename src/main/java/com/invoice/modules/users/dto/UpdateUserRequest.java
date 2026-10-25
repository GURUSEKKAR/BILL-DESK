package com.invoice.modules.users.dto;

import java.util.UUID;

/**
 * Reassign a cashier to another counter and/or (de)activate them.
 * All fields optional — partial update.
 */
public record UpdateUserRequest(
    UUID counterId,
    Boolean active
) {}
