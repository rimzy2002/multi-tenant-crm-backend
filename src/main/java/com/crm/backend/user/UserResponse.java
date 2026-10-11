package com.crm.backend.user;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record UserResponse(
        Long id,
        Long tenantId,
        String email,
        String firstName,
        String lastName,
        Role role,
        @JsonProperty("isActive")
        boolean isActive,
        Instant createdAt,
        Instant updatedAt
) {

    @JsonProperty("active")
    public boolean active() {
        return isActive;
    }

    public static UserResponse fromEntity(User user) {
        return new UserResponse(
                user.getId(),
                user.getTenantId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole(),
                user.isActive(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
