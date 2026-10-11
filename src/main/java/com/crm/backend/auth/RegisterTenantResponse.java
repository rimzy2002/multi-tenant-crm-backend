package com.crm.backend.auth;

import java.time.Instant;

public record RegisterTenantResponse(
        Long tenantId,
        String tenantCode,
        String companyName,
        Long adminUserId,
        String adminEmail,
        String adminFullName,
        String role,
        Instant createdAt
) {
}
