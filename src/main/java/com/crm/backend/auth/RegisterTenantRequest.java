package com.crm.backend.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterTenantRequest(
        @NotBlank(message = "Company name is required")
        @Size(max = 100, message = "Company name cannot exceed 100 characters")
        String companyName,

        @NotBlank(message = "Tenant code is required")
        @Size(min = 2, max = 50, message = "Tenant code must be between 2 and 50 characters")
        @Pattern(regexp = "^[a-z0-9-]+$", message = "Tenant code must contain only lowercase letters, digits, and hyphens")
        String tenantCode,

        @NotBlank(message = "First name is required")
        @Size(max = 50, message = "First name cannot exceed 50 characters")
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(max = 50, message = "Last name cannot exceed 50 characters")
        String lastName,

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        @Size(max = 120, message = "Email cannot exceed 120 characters")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 100, message = "Password must be at least 8 characters")
        String password
) {
}
