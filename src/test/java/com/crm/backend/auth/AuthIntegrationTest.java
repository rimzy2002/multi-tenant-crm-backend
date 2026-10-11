package com.crm.backend.auth;

import com.crm.backend.customer.CustomerRepository;
import com.crm.backend.security.JwtService;
import com.crm.backend.tenant.Tenant;
import com.crm.backend.tenant.TenantRepository;
import com.crm.backend.user.Role;
import com.crm.backend.user.User;
import com.crm.backend.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        customerRepository.deleteAll();
        userRepository.deleteAll();
        tenantRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        customerRepository.deleteAll();
        userRepository.deleteAll();
        tenantRepository.deleteAll();
    }

    @Test
    @DisplayName("Should successfully register a new tenant and initial administrator")
    void testSuccessfulTenantRegistration() throws Exception {
        RegisterTenantRequest request = new RegisterTenantRequest(
                "Apex Solutions",
                "apex-solutions",
                "John",
                "Doe",
                "admin@apex.com",
                "SecureP@ss123"
        );

        mockMvc.perform(post("/api/v1/auth/register-tenant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").isNumber())
                .andExpect(jsonPath("$.tenantCode").value("apex-solutions"))
                .andExpect(jsonPath("$.companyName").value("Apex Solutions"))
                .andExpect(jsonPath("$.adminUserId").isNumber())
                .andExpect(jsonPath("$.adminEmail").value("admin@apex.com"))
                .andExpect(jsonPath("$.adminFullName").value("John Doe"))
                .andExpect(jsonPath("$.role").value("TENANT_ADMIN"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        // Verify entity persisted with hashed password
        Optional<Tenant> tenantOpt = tenantRepository.findByTenantCode("apex-solutions");
        assertTrue(tenantOpt.isPresent());
        Tenant tenant = tenantOpt.get();
        assertTrue(tenant.isActive());

        Optional<User> userOpt = userRepository.findByTenantIdAndEmail(tenant.getId(), "admin@apex.com");
        assertTrue(userOpt.isPresent());
        User user = userOpt.get();
        assertEquals(Role.TENANT_ADMIN, user.getRole());
        assertTrue(user.isActive());
        assertTrue(passwordEncoder.matches("SecureP@ss123", user.getPasswordHash()));
        assertTrue(user.getPasswordHash().startsWith("$2a$"), "Password should be hashed with BCrypt");
    }

    @Test
    @DisplayName("Should reject registration with duplicate tenant code")
    void testDuplicateTenantRegistration() throws Exception {
        RegisterTenantRequest request = new RegisterTenantRequest(
                "Beta Corp",
                "beta-corp",
                "Jane",
                "Smith",
                "jane@beta.com",
                "P@ssword123"
        );

        // First registration succeeds
        mockMvc.perform(post("/api/v1/auth/register-tenant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Duplicate registration fails with 409 Conflict
        mockMvc.perform(post("/api/v1/auth/register-tenant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("already registered")));
    }

    @Test
    @DisplayName("Should successfully authenticate tenant user and issue signed JWT")
    void testSuccessfulLogin() throws Exception {
        registerTestTenant("gamma-inc", "Gamma Inc", "gamma@gamma.com", "Password123");

        LoginRequest loginRequest = new LoginRequest("gamma-inc", "gamma@gamma.com", "Password123");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.tenantCode").value("gamma-inc"))
                .andExpect(jsonPath("$.email").value("gamma@gamma.com"))
                .andExpect(jsonPath("$.role").value("TENANT_ADMIN"));
    }

    @Test
    @DisplayName("Should reject login with incorrect password")
    void testLoginWithIncorrectPassword() throws Exception {
        registerTestTenant("delta-ltd", "Delta Ltd", "delta@delta.com", "CorrectPass123");

        LoginRequest loginRequest = new LoginRequest("delta-ltd", "delta@delta.com", "WrongPassword");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", containsString("Invalid tenant code, email, or password")));
    }

    @Test
    @DisplayName("Should reject login with incorrect tenant code")
    void testLoginWithIncorrectTenantCode() throws Exception {
        registerTestTenant("epsilon-co", "Epsilon Co", "eps@epsilon.com", "CorrectPass123");

        LoginRequest loginRequest = new LoginRequest("non-existent-tenant", "eps@epsilon.com", "CorrectPass123");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", containsString("Invalid tenant code, email, or password")));
    }

    @Test
    @DisplayName("Should reject login when tenant is deactivated")
    void testLoginWithInactiveTenant() throws Exception {
        Tenant tenant = registerTestTenant("zeta-holdings", "Zeta Holdings", "zeta@zeta.com", "Pass12345");
        tenant.setActive(false);
        tenantRepository.save(tenant);

        LoginRequest loginRequest = new LoginRequest("zeta-holdings", "zeta@zeta.com", "Pass12345");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("Tenant account is deactivated")));
    }

    @Test
    @DisplayName("Should reject login when user account is deactivated")
    void testLoginWithInactiveUser() throws Exception {
        Tenant tenant = registerTestTenant("eta-enterprises", "Eta Enterprises", "eta@eta.com", "Pass12345");
        User user = userRepository.findByTenantIdAndEmail(tenant.getId(), "eta@eta.com").orElseThrow();
        user.setActive(false);
        userRepository.save(user);

        LoginRequest loginRequest = new LoginRequest("eta-enterprises", "eta@eta.com", "Pass12345");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("User account is deactivated")));
    }

    @Test
    @DisplayName("Should access protected endpoint with valid JWT")
    void testAccessProtectedEndpointWithValidJwt() throws Exception {
        registerTestTenant("theta-group", "Theta Group", "theta@theta.com", "Pass12345");

        String token = obtainAccessToken("theta-group", "theta@theta.com", "Pass12345");

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("theta@theta.com"))
                .andExpect(jsonPath("$.tenantCode").value("theta-group"))
                .andExpect(jsonPath("$.role").value("TENANT_ADMIN"));
    }

    @Test
    @DisplayName("Should reject access to protected endpoint without token")
    void testAccessProtectedEndpointWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", containsString("Full authentication is required")));
    }

    @Test
    @DisplayName("Should reject access with malformed/invalid JWT")
    void testAccessProtectedEndpointWithInvalidJwt() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer invalid.malformed.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should reject access with expired JWT")
    void testAccessProtectedEndpointWithExpiredJwt() throws Exception {
        Tenant tenant = registerTestTenant("iota-corp", "Iota Corp", "iota@iota.com", "Pass12345");
        User user = userRepository.findByTenantIdAndEmail(tenant.getId(), "iota@iota.com").orElseThrow();

        // Generate token with negative expiration (already expired)
        JwtService shortLivedJwtService = new JwtService("404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970", -1000);
        String expiredToken = shortLivedJwtService.generateToken(user.getId(), tenant.getId(), tenant.getTenantCode(), user.getEmail(), user.getRole().name());

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should allow TENANT_ADMIN on admin-only endpoint and forbid SALES_REP")
    void testRoleBasedAccessRestrictions() throws Exception {
        Tenant tenant = registerTestTenant("kappa-tech", "Kappa Tech", "admin@kappa.com", "Pass12345");

        // 1. Admin logs in and accesses admin-only endpoint
        String adminToken = obtainAccessToken("kappa-tech", "admin@kappa.com", "Pass12345");
        mockMvc.perform(get("/api/v1/auth/admin-only")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("TENANT_ADMIN"));

        // 2. Create a SALES_REP user in the same tenant
        User salesRep = new User(tenant.getId(), "sales@kappa.com", passwordEncoder.encode("SalesPass123"), "Sam", "Rep", Role.SALES_REP);
        userRepository.save(salesRep);

        String salesToken = obtainAccessToken("kappa-tech", "sales@kappa.com", "SalesPass123");

        // 3. Sales rep is forbidden from admin-only endpoint
        mockMvc.perform(get("/api/v1/auth/admin-only")
                        .header("Authorization", "Bearer " + salesToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("insufficient permissions")));
    }

    @Test
    @DisplayName("Should reject previously issued JWT if tenant is deactivated after issuance")
    void testTokenRevocationOnTenantDeactivation() throws Exception {
        Tenant tenant = registerTestTenant("lambda-llc", "Lambda LLC", "lambda@lambda.com", "Pass12345");
        String token = obtainAccessToken("lambda-llc", "lambda@lambda.com", "Pass12345");

        // Deactivate tenant in database
        tenant.setActive(false);
        tenantRepository.save(tenant);

        // Even with valid unexpired token, access is rejected
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should reject previously issued JWT if user is deactivated after issuance")
    void testTokenRevocationOnUserDeactivation() throws Exception {
        Tenant tenant = registerTestTenant("mu-systems", "Mu Systems", "mu@mu.com", "Pass12345");
        String token = obtainAccessToken("mu-systems", "mu@mu.com", "Pass12345");

        // Deactivate user in database
        User user = userRepository.findByTenantIdAndEmail(tenant.getId(), "mu@mu.com").orElseThrow();
        user.setActive(false);
        userRepository.save(user);

        // Even with valid unexpired token, access is rejected
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    private Tenant registerTestTenant(String tenantCode, String companyName, String email, String password) {
        Tenant tenant = new Tenant(tenantCode, companyName);
        tenant = tenantRepository.save(tenant);

        User admin = new User(tenant.getId(), email, passwordEncoder.encode(password), "Test", "Admin", Role.TENANT_ADMIN);
        userRepository.save(admin);
        return tenant;
    }

    private String obtainAccessToken(String tenantCode, String email, String password) throws Exception {
        LoginRequest request = new LoginRequest(tenantCode, email, password);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse authResponse = objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
        return authResponse.accessToken();
    }
}
