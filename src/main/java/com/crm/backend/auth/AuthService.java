package com.crm.backend.auth;

import com.crm.backend.common.AccountDisabledException;
import com.crm.backend.common.DuplicateResourceException;
import com.crm.backend.common.InvalidCredentialsException;
import com.crm.backend.security.JwtService;
import com.crm.backend.security.UserPrincipal;
import com.crm.backend.tenant.Tenant;
import com.crm.backend.tenant.TenantRepository;
import com.crm.backend.user.Role;
import com.crm.backend.user.User;
import com.crm.backend.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AuthService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            TenantRepository tenantRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public RegisterTenantResponse registerTenant(RegisterTenantRequest request) {
        String normalizedTenantCode = request.tenantCode().trim().toLowerCase();
        String normalizedEmail = request.email().trim().toLowerCase();

        if (tenantRepository.existsByTenantCode(normalizedTenantCode)) {
            throw new DuplicateResourceException("Tenant code '" + normalizedTenantCode + "' is already registered");
        }

        // 1. Create Tenant
        Tenant tenant = new Tenant(normalizedTenantCode, request.companyName().trim());
        tenant = tenantRepository.save(tenant);

        // 2. Create Initial Administrator User
        String passwordHash = passwordEncoder.encode(request.password());
        User adminUser = new User(
                tenant.getId(),
                normalizedEmail,
                passwordHash,
                request.firstName().trim(),
                request.lastName().trim(),
                Role.TENANT_ADMIN
        );
        adminUser = userRepository.save(adminUser);

        return new RegisterTenantResponse(
                tenant.getId(),
                tenant.getTenantCode(),
                tenant.getCompanyName(),
                adminUser.getId(),
                adminUser.getEmail(),
                adminUser.getFirstName() + " " + adminUser.getLastName(),
                adminUser.getRole().name(),
                tenant.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String normalizedTenantCode = request.tenantCode().trim().toLowerCase();
        String normalizedEmail = request.email().trim().toLowerCase();

        // 1. Verify Tenant
        Optional<Tenant> tenantOpt = tenantRepository.findByTenantCode(normalizedTenantCode);
        if (tenantOpt.isEmpty()) {
            throw new InvalidCredentialsException("Invalid tenant code, email, or password");
        }

        Tenant tenant = tenantOpt.get();
        if (!tenant.isActive()) {
            throw new AccountDisabledException("Tenant account is deactivated");
        }

        // 2. Verify User within specified Tenant
        Optional<User> userOpt = userRepository.findByTenantIdAndEmail(tenant.getId(), normalizedEmail);
        if (userOpt.isEmpty()) {
            throw new InvalidCredentialsException("Invalid tenant code, email, or password");
        }

        User user = userOpt.get();
        if (!user.isActive()) {
            throw new AccountDisabledException("User account is deactivated");
        }

        // 3. Verify Password
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid tenant code, email, or password");
        }

        // 4. Generate JWT
        String token = jwtService.generateToken(
                user.getId(),
                tenant.getId(),
                tenant.getTenantCode(),
                user.getEmail(),
                user.getRole().name()
        );

        return AuthResponse.of(
                token,
                jwtService.getExpirationMs(),
                tenant.getId(),
                tenant.getTenantCode(),
                user.getId(),
                user.getEmail(),
                user.getRole().name()
        );
    }

    public UserProfileResponse getProfile(UserPrincipal principal) {
        return new UserProfileResponse(
                principal.getUserId(),
                principal.getTenantId(),
                principal.getTenantCode(),
                principal.getUsername(),
                "", // will be enriched if full user details needed
                "",
                principal.getRole().name()
        );
    }
}
