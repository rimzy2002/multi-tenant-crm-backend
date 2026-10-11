package com.crm.backend.auth;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        Long tenantId,
        String tenantCode,
        Long userId,
        String email,
        String role
) {
    public static AuthResponse of(String token, long expiresIn, Long tenantId, String tenantCode, Long userId, String email, String role) {
        return new AuthResponse(token, "Bearer", expiresIn, tenantId, tenantCode, userId, email, role);
    }
}
