package com.crm.backend.customer;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record CustomerResponse(
        Long id,
        Long tenantId,
        String firstName,
        String lastName,
        String name,
        String email,
        String phone,
        String company,
        String address,
        String notes,
        @JsonProperty("isActive")
        boolean isActive,
        Instant createdAt,
        Instant updatedAt
) {

    @JsonProperty("active")
    public boolean active() {
        return isActive;
    }

    public static CustomerResponse fromEntity(Customer customer) {
        String fullName = ((customer.getFirstName() != null ? customer.getFirstName() : "") + " "
                + (customer.getLastName() != null ? customer.getLastName() : "")).trim();

        return new CustomerResponse(
                customer.getId(),
                customer.getTenantId(),
                customer.getFirstName(),
                customer.getLastName(),
                fullName,
                customer.getEmail(),
                customer.getPhone(),
                customer.getCompany(),
                customer.getAddress(),
                customer.getNotes(),
                customer.isActive(),
                customer.getCreatedAt(),
                customer.getUpdatedAt()
        );
    }
}
