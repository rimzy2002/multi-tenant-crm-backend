package com.crm.backend.user;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @NotBlank(message = "First name is required")
        @Size(max = 50, message = "First name cannot exceed 50 characters")
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(max = 50, message = "Last name cannot exceed 50 characters")
        String lastName,

        @Email(message = "Valid email is required")
        @Size(max = 120, message = "Email cannot exceed 120 characters")
        String email,

        @NotNull(message = "Role is required")
        Role role,

        @JsonAlias({"isActive", "active"})
        Boolean active
) {
}
