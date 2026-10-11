package com.crm.backend.auth;

public record UserProfileResponse(
        Long userId,
        Long tenantId,
        String tenantCode,
        String email,
        String firstName,
        String lastName,
        String role
) {
}
