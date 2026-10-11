package com.crm.backend.customer;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record CreateCustomerRequest(
        @JsonAlias("firstName")
        @Size(max = 50, message = "First name cannot exceed 50 characters")
        String firstName,

        @JsonAlias("lastName")
        @Size(max = 50, message = "Last name cannot exceed 50 characters")
        String lastName,

        @JsonAlias("name")
        String name,

        @Email(message = "Valid email is required")
        @Size(max = 120, message = "Email cannot exceed 120 characters")
        String email,

        @Size(max = 30, message = "Phone cannot exceed 30 characters")
        String phone,

        @Size(max = 100, message = "Company cannot exceed 100 characters")
        String company,

        @Size(max = 255, message = "Address cannot exceed 255 characters")
        String address,

        String notes
) {
    public CreateCustomerRequest {
        if ((firstName == null || firstName.isBlank()) && name != null && !name.isBlank()) {
            String trimmed = name.trim();
            String[] parts = trimmed.split("\\s+", 2);
            firstName = parts[0];
            lastName = parts.length > 1 ? parts[1] : parts[0];
        }
    }

    public String resolveFirstName() {
        return firstName != null ? firstName.trim() : "";
    }

    public String resolveLastName() {
        return lastName != null ? lastName.trim() : "";
    }
}
