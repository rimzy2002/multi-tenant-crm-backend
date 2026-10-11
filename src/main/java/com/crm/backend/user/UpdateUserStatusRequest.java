package com.crm.backend.user;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(
        @NotNull(message = "Active status is required")
        @JsonAlias({"isActive", "active", "status"})
        Boolean active
) {
}
