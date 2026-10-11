package com.crm.backend.auth;

import com.crm.backend.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register-tenant")
    public ResponseEntity<RegisterTenantResponse> registerTenant(@Valid @RequestBody RegisterTenantRequest request) {
        RegisterTenantResponse response = authService.registerTenant(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<UserPrincipalDto> getCurrentUser(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(new UserPrincipalDto(
                principal.getUserId(),
                principal.getTenantId(),
                principal.getTenantCode(),
                principal.getUsername(),
                principal.getRole().name()
        ));
    }

    @GetMapping("/admin-only")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<Map<String, String>> adminOnlyEndpoint(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(Map.of(
                "message", "Admin access granted for tenant: " + principal.getTenantCode(),
                "role", principal.getRole().name()
        ));
    }

    public record UserPrincipalDto(
            Long userId,
            Long tenantId,
            String tenantCode,
            String email,
            String role
    ) {
    }
}
