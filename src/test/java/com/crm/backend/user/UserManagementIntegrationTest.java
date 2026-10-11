package com.crm.backend.user;

import com.crm.backend.auth.AuthResponse;
import com.crm.backend.auth.LoginRequest;
import com.crm.backend.customer.CustomerRepository;
import com.crm.backend.tenant.Tenant;
import com.crm.backend.tenant.TenantRepository;
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

import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class UserManagementIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private com.crm.backend.customer.CustomerRepository customerRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

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
    @DisplayName("Admin creates a user successfully and password hash is never exposed")
    void testAdminCreatesUserSuccessfully() throws Exception {
        Tenant tenant = registerTenantWithAdmin("alpha-corp", "Alpha Corp", "admin@alpha.com", "AdminPass123");
        String adminToken = loginAndGetToken("alpha-corp", "admin@alpha.com", "AdminPass123");

        CreateUserRequest request = new CreateUserRequest(
                "Sarah",
                "Connor",
                "sarah@alpha.com",
                "P@ssword123",
                Role.SALES_REP
        );

        MvcResult result = mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.tenantId").value(tenant.getId()))
                .andExpect(jsonPath("$.email").value("sarah@alpha.com"))
                .andExpect(jsonPath("$.firstName").value("Sarah"))
                .andExpect(jsonPath("$.lastName").value("Connor"))
                .andExpect(jsonPath("$.role").value("SALES_REP"))
                .andExpect(jsonPath("$.isActive").value(true))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();

        UserResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), UserResponse.class);
        Optional<User> savedUserOpt = userRepository.findById(response.id());
        assertTrue(savedUserOpt.isPresent());
        User savedUser = savedUserOpt.get();
        assertEquals(tenant.getId(), savedUser.getTenantId());
        assertEquals("sarah@alpha.com", savedUser.getEmail());
        assertTrue(passwordEncoder.matches("P@ssword123", savedUser.getPasswordHash()));
        assertTrue(savedUser.getPasswordHash().startsWith("$2a$"), "Password must be BCrypt hashed");
    }

    @Test
    @DisplayName("Admin creates another TENANT_ADMIN user successfully")
    void testAdminCreatesAnotherAdminUserSuccessfully() throws Exception {
        Tenant tenant = registerTenantWithAdmin("beta-corp", "Beta Corp", "admin@beta.com", "AdminPass123");
        String adminToken = loginAndGetToken("beta-corp", "admin@beta.com", "AdminPass123");

        CreateUserRequest request = new CreateUserRequest(
                "James",
                "Miller",
                "james.admin@beta.com",
                "StrongAdminPass123",
                Role.TENANT_ADMIN
        );

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").value(tenant.getId()))
                .andExpect(jsonPath("$.role").value("TENANT_ADMIN"))
                .andExpect(jsonPath("$.email").value("james.admin@beta.com"));
    }

    @Test
    @DisplayName("Reject user creation with duplicate email within the same tenant")
    void testRejectDuplicateEmailInSameTenant() throws Exception {
        registerTenantWithAdmin("gamma-corp", "Gamma Corp", "admin@gamma.com", "AdminPass123");
        String adminToken = loginAndGetToken("gamma-corp", "admin@gamma.com", "AdminPass123");

        CreateUserRequest initialRequest = new CreateUserRequest(
                "Alex",
                "Stone",
                "alex@gamma.com",
                "Password123",
                Role.SALES_REP
        );

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(initialRequest)))
                .andExpect(status().isCreated());

        // Attempt duplicate with same email
        CreateUserRequest duplicateRequest = new CreateUserRequest(
                "Alexander",
                "Stone",
                "alex@gamma.com",
                "AnotherPass123",
                Role.SALES_REP
        );

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("already exists in this tenant")));

        // Attempt duplicate with case-insensitive variation
        CreateUserRequest caseVariantRequest = new CreateUserRequest(
                "Alexander",
                "Stone",
                "ALEX@gamma.com",
                "AnotherPass123",
                Role.SALES_REP
        );

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(caseVariantRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("already exists in this tenant")));
    }

    @Test
    @DisplayName("Allow same email address across different tenants")
    void testAllowSameEmailInDifferentTenants() throws Exception {
        registerTenantWithAdmin("tenant-one", "Tenant One", "admin@tenant1.com", "Pass12345");
        String tokenOne = loginAndGetToken("tenant-one", "admin@tenant1.com", "Pass12345");

        registerTenantWithAdmin("tenant-two", "Tenant Two", "admin@tenant2.com", "Pass12345");
        String tokenTwo = loginAndGetToken("tenant-two", "admin@tenant2.com", "Pass12345");

        CreateUserRequest requestOne = new CreateUserRequest("Common", "User", "shared@example.com", "Password123", Role.SALES_REP);
        CreateUserRequest requestTwo = new CreateUserRequest("Common", "User", "shared@example.com", "Password123", Role.SALES_REP);

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + tokenOne)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestOne)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + tokenTwo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestTwo)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Tenant-scoped user listing returns only current tenant users with pagination")
    void testTenantScopedUserListingWithPagination() throws Exception {
        Tenant tenantA = registerTenantWithAdmin("tenant-a", "Tenant A", "admin@a.com", "Pass12345");
        String tokenA = loginAndGetToken("tenant-a", "admin@a.com", "Pass12345");

        Tenant tenantB = registerTenantWithAdmin("tenant-b", "Tenant B", "admin@b.com", "Pass12345");
        loginAndGetToken("tenant-b", "admin@b.com", "Pass12345");

        // Add 2 more users to Tenant A (total 3 users)
        createTestUser(tenantA.getId(), "user1@a.com", "User", "One", Role.SALES_REP);
        createTestUser(tenantA.getId(), "user2@a.com", "User", "Two", Role.SALES_REP);

        // Add 1 more user to Tenant B (total 2 users)
        createTestUser(tenantB.getId(), "user1@b.com", "User", "B-One", Role.SALES_REP);

        // Fetch all users for Tenant A
        mockMvc.perform(get("/api/v1/users?page=0&size=10")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.content[*].tenantId", everyItem(is(tenantA.getId().intValue()))))
                .andExpect(jsonPath("$.content[*].password").doesNotExist())
                .andExpect(jsonPath("$.content[*].passwordHash").doesNotExist());

        // Test pagination: page 0, size 2
        mockMvc.perform(get("/api/v1/users?page=0&size=2")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content", hasSize(2)));

        // Test pagination: page 1, size 2
        mockMvc.perform(get("/api/v1/users?page=1&size=2")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    @DisplayName("Safe sorting on user listing rejects invalid sort properties")
    void testSafeSortingOnUserListing() throws Exception {
        Tenant tenant = registerTenantWithAdmin("sort-tenant", "Sort Tenant", "admin@sort.com", "Pass12345");
        String token = loginAndGetToken("sort-tenant", "admin@sort.com", "Pass12345");

        createTestUser(tenant.getId(), "charlie@sort.com", "Charlie", "Brown", Role.SALES_REP);
        createTestUser(tenant.getId(), "alice@sort.com", "Alice", "Wonderland", Role.SALES_REP);

        // Valid sort by email ascending
        mockMvc.perform(get("/api/v1/users?sort=email,asc")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("admin@sort.com"))
                .andExpect(jsonPath("$.content[1].email").value("alice@sort.com"))
                .andExpect(jsonPath("$.content[2].email").value("charlie@sort.com"));

        // Invalid/unsafe sort property rejected with 400
        mockMvc.perform(get("/api/v1/users?sort=passwordHash,asc")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Invalid sort property")));
    }

    @Test
    @DisplayName("Lookup user by ID within tenant succeeds")
    void testGetUserByIdWithinTenant() throws Exception {
        Tenant tenant = registerTenantWithAdmin("lookup-tenant", "Lookup Co", "admin@lookup.com", "Pass12345");
        String token = loginAndGetToken("lookup-tenant", "admin@lookup.com", "Pass12345");

        User user = createTestUser(tenant.getId(), "rep@lookup.com", "Sales", "Rep", Role.SALES_REP);

        mockMvc.perform(get("/api/v1/users/" + user.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId()))
                .andExpect(jsonPath("$.tenantId").value(tenant.getId()))
                .andExpect(jsonPath("$.email").value("rep@lookup.com"))
                .andExpect(jsonPath("$.firstName").value("Sales"))
                .andExpect(jsonPath("$.lastName").value("Rep"))
                .andExpect(jsonPath("$.role").value("SALES_REP"))
                .andExpect(jsonPath("$.isActive").value(true))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("Lookup nonexistent user returns 404")
    void testGetNonexistentUserReturns404() throws Exception {
        registerTenantWithAdmin("notfound-tenant", "NotFound Co", "admin@nf.com", "Pass12345");
        String token = loginAndGetToken("notfound-tenant", "admin@nf.com", "Pass12345");

        mockMvc.perform(get("/api/v1/users/999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("User not found with id: 999999")));
    }

    @Test
    @DisplayName("Cross-tenant user lookup returns 404 without leaking existence")
    void testCrossTenantUserLookupReturns404() throws Exception {
        registerTenantWithAdmin("tenant-x", "Tenant X", "admin@x.com", "Pass12345");
        String tokenX = loginAndGetToken("tenant-x", "admin@x.com", "Pass12345");

        Tenant tenantY = registerTenantWithAdmin("tenant-y", "Tenant Y", "admin@y.com", "Pass12345");
        User userY = createTestUser(tenantY.getId(), "user@y.com", "User", "Y", Role.SALES_REP);

        // Tenant X admin attempts to retrieve Tenant Y user
        mockMvc.perform(get("/api/v1/users/" + userY.getId())
                        .header("Authorization", "Bearer " + tokenX))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("User not found with id: " + userY.getId())));
    }

    @Test
    @DisplayName("Admin updates user details within tenant successfully")
    void testUpdateUserWithinTenantSuccessfully() throws Exception {
        Tenant tenant = registerTenantWithAdmin("update-tenant", "Update Co", "admin@update.com", "Pass12345");
        String token = loginAndGetToken("update-tenant", "admin@update.com", "Pass12345");

        User user = createTestUser(tenant.getId(), "old.email@update.com", "OldFirst", "OldLast", Role.SALES_REP);

        UpdateUserRequest updateRequest = new UpdateUserRequest(
                "NewFirst",
                "NewLast",
                "new.email@update.com",
                Role.TENANT_ADMIN,
                true
        );

        mockMvc.perform(put("/api/v1/users/" + user.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId()))
                .andExpect(jsonPath("$.email").value("new.email@update.com"))
                .andExpect(jsonPath("$.firstName").value("NewFirst"))
                .andExpect(jsonPath("$.lastName").value("NewLast"))
                .andExpect(jsonPath("$.role").value("TENANT_ADMIN"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        User updated = userRepository.findById(user.getId()).orElseThrow();
        assertEquals("new.email@update.com", updated.getEmail());
        assertEquals("NewFirst", updated.getFirstName());
        assertEquals("NewLast", updated.getLastName());
        assertEquals(Role.TENANT_ADMIN, updated.getRole());
    }

    @Test
    @DisplayName("Reject user update with duplicate email within same tenant")
    void testRejectUpdateWithDuplicateEmail() throws Exception {
        Tenant tenant = registerTenantWithAdmin("dup-update", "Dup Co", "admin@dup.com", "Pass12345");
        String token = loginAndGetToken("dup-update", "admin@dup.com", "Pass12345");

        User user1 = createTestUser(tenant.getId(), "first@dup.com", "First", "User", Role.SALES_REP);
        User user2 = createTestUser(tenant.getId(), "second@dup.com", "Second", "User", Role.SALES_REP);

        UpdateUserRequest updateRequest = new UpdateUserRequest(
                "Second",
                "User",
                "first@dup.com", // collision with user1
                Role.SALES_REP,
                true
        );

        mockMvc.perform(put("/api/v1/users/" + user2.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("already exists in this tenant")));
    }

    @Test
    @DisplayName("Cross-tenant user update returns 404")
    void testCrossTenantUserUpdateReturns404() throws Exception {
        registerTenantWithAdmin("tenant-up-x", "Tenant UP X", "admin@upx.com", "Pass12345");
        String tokenX = loginAndGetToken("tenant-up-x", "admin@upx.com", "Pass12345");

        Tenant tenantY = registerTenantWithAdmin("tenant-up-y", "Tenant UP Y", "admin@upy.com", "Pass12345");
        User userY = createTestUser(tenantY.getId(), "target@upy.com", "Target", "Y", Role.SALES_REP);

        UpdateUserRequest updateRequest = new UpdateUserRequest("Hacked", "Hacked", "hacked@upy.com", Role.SALES_REP, true);

        mockMvc.perform(put("/api/v1/users/" + userY.getId())
                        .header("Authorization", "Bearer " + tokenX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("User not found with id: " + userY.getId())));
    }

    @Test
    @DisplayName("Update user status (activation and deactivation) successfully")
    void testUpdateUserStatus() throws Exception {
        Tenant tenant = registerTenantWithAdmin("status-tenant", "Status Co", "admin@status.com", "Pass12345");
        String token = loginAndGetToken("status-tenant", "admin@status.com", "Pass12345");

        User user = createTestUser(tenant.getId(), "rep@status.com", "Active", "Rep", Role.SALES_REP);
        assertTrue(user.isActive());

        // Deactivate user
        UpdateUserStatusRequest deactivateRequest = new UpdateUserStatusRequest(false);
        mockMvc.perform(patch("/api/v1/users/" + user.getId() + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deactivateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId()))
                .andExpect(jsonPath("$.isActive").value(false))
                .andExpect(jsonPath("$.active").value(false));

        User deactivated = userRepository.findById(user.getId()).orElseThrow();
        assertFalse(deactivated.isActive());

        // Reactivate user using alternative alias "isActive"
        mockMvc.perform(patch("/api/v1/users/" + user.getId() + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId()))
                .andExpect(jsonPath("$.isActive").value(true))
                .andExpect(jsonPath("$.active").value(true));

        User reactivated = userRepository.findById(user.getId()).orElseThrow();
        assertTrue(reactivated.isActive());
    }

    @Test
    @DisplayName("Cross-tenant user status update returns 404")
    void testCrossTenantUserStatusUpdateReturns404() throws Exception {
        registerTenantWithAdmin("status-x", "Status X", "admin@statusx.com", "Pass12345");
        String tokenX = loginAndGetToken("status-x", "admin@statusx.com", "Pass12345");

        Tenant tenantY = registerTenantWithAdmin("status-y", "Status Y", "admin@statusy.com", "Pass12345");
        User userY = createTestUser(tenantY.getId(), "victim@statusy.com", "Victim", "User", Role.SALES_REP);

        UpdateUserStatusRequest request = new UpdateUserStatusRequest(false);
        mockMvc.perform(patch("/api/v1/users/" + userY.getId() + "/status")
                        .header("Authorization", "Bearer " + tokenX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("User not found with id: " + userY.getId())));
    }

    @Test
    @DisplayName("SALES_REP role is forbidden from all user management endpoints")
    void testSalesRepForbiddenFromUserManagement() throws Exception {
        Tenant tenant = registerTenantWithAdmin("sales-corp", "Sales Corp", "admin@salescorp.com", "AdminPass123");
        createTestUser(tenant.getId(), "salesrep@salescorp.com", "Sam", "Sales", Role.SALES_REP);
        String salesToken = loginAndGetToken("sales-corp", "salesrep@salescorp.com", "Pass12345");

        CreateUserRequest createRequest = new CreateUserRequest("New", "User", "new@salescorp.com", "Pass12345", Role.SALES_REP);
        UpdateUserRequest updateRequest = new UpdateUserRequest("Sam", "Sales", "salesrep@salescorp.com", Role.SALES_REP, true);
        UpdateUserStatusRequest statusRequest = new UpdateUserStatusRequest(false);

        // POST /api/v1/users -> 403
        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + salesToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        // GET /api/v1/users -> 403
        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + salesToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        // GET /api/v1/users/1 -> 403
        mockMvc.perform(get("/api/v1/users/1")
                        .header("Authorization", "Bearer " + salesToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        // PUT /api/v1/users/1 -> 403
        mockMvc.perform(put("/api/v1/users/1")
                        .header("Authorization", "Bearer " + salesToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        // PATCH /api/v1/users/1/status -> 403
        mockMvc.perform(patch("/api/v1/users/1/status")
                        .header("Authorization", "Bearer " + salesToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("Unauthenticated requests return 401 Unauthorized")
    void testUnauthenticatedRequestsReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("Validation rejects invalid inputs on user creation")
    void testValidationRejectsInvalidInputs() throws Exception {
        registerTenantWithAdmin("val-corp", "Val Corp", "admin@val.com", "AdminPass123");
        String token = loginAndGetToken("val-corp", "admin@val.com", "AdminPass123");

        // Missing fields / invalid email / short password
        CreateUserRequest invalidRequest = new CreateUserRequest(
                "",
                "",
                "not-an-email",
                "short",
                null
        );

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.firstName").isNotEmpty())
                .andExpect(jsonPath("$.errors.lastName").isNotEmpty())
                .andExpect(jsonPath("$.errors.email").isNotEmpty())
                .andExpect(jsonPath("$.errors.password").isNotEmpty())
                .andExpect(jsonPath("$.errors.role").isNotEmpty());
    }

    @Test
    @DisplayName("Prevent tenant reassignment through request payload")
    void testPreventTenantReassignmentThroughPayload() throws Exception {
        Tenant tenantA = registerTenantWithAdmin("tenant-tamper", "Tamper Co", "admin@tamper.com", "AdminPass123");
        String token = loginAndGetToken("tenant-tamper", "admin@tamper.com", "AdminPass123");

        // Attempt to pass malicious tenantId in body
        Map<String, Object> payloadWithSpoofedTenant = Map.of(
                "tenantId", 99999L,
                "firstName", "Innocent",
                "lastName", "User",
                "email", "innocent@tamper.com",
                "password", "Password123!",
                "role", "SALES_REP"
        );

        MvcResult result = mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payloadWithSpoofedTenant)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").value(tenantA.getId()))
                .andReturn();

        UserResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), UserResponse.class);
        User savedUser = userRepository.findById(response.id()).orElseThrow();
        assertEquals(tenantA.getId(), savedUser.getTenantId(), "User MUST be assigned strictly to authenticated admin's tenant");
        assertNotEquals(99999L, savedUser.getTenantId());
    }

    @Test
    @DisplayName("Prevent accidental deactivation or demotion of final active TENANT_ADMIN")
    void testPreventDeactivatingOrDemotingSoleActiveAdmin() throws Exception {
        Tenant tenant = registerTenantWithAdmin("sole-admin-co", "Sole Admin Co", "sole.admin@sole.com", "AdminPass123");
        String token = loginAndGetToken("sole-admin-co", "sole.admin@sole.com", "AdminPass123");
        User admin = userRepository.findByTenantIdAndEmail(tenant.getId(), "sole.admin@sole.com").orElseThrow();

        // 1. Attempt to deactivate the sole admin
        UpdateUserStatusRequest deactivateRequest = new UpdateUserStatusRequest(false);
        mockMvc.perform(patch("/api/v1/users/" + admin.getId() + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deactivateRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Cannot deactivate the only active administrator")));

        // 2. Attempt to demote the sole admin to SALES_REP
        UpdateUserRequest demoteRequest = new UpdateUserRequest(
                admin.getFirstName(),
                admin.getLastName(),
                admin.getEmail(),
                Role.SALES_REP,
                true
        );
        mockMvc.perform(put("/api/v1/users/" + admin.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(demoteRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Cannot demote or deactivate the only active administrator")));

        // 3. Add second admin, then deactivating first admin should succeed
        User secondAdmin = new User(tenant.getId(), "second.admin@sole.com", passwordEncoder.encode("Pass12345"), "Second", "Admin", Role.TENANT_ADMIN);
        userRepository.save(secondAdmin);

        mockMvc.perform(patch("/api/v1/users/" + admin.getId() + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deactivateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(false));
    }

    private Tenant registerTenantWithAdmin(String tenantCode, String companyName, String adminEmail, String adminPassword) {
        Tenant tenant = new Tenant(tenantCode, companyName);
        tenant = tenantRepository.save(tenant);

        User admin = new User(
                tenant.getId(),
                adminEmail,
                passwordEncoder.encode(adminPassword),
                "Admin",
                "User",
                Role.TENANT_ADMIN
        );
        userRepository.save(admin);
        return tenant;
    }

    private User createTestUser(Long tenantId, String email, String firstName, String lastName, Role role) {
        User user = new User(
                tenantId,
                email,
                passwordEncoder.encode("Pass12345"),
                firstName,
                lastName,
                role
        );
        return userRepository.save(user);
    }

    private String loginAndGetToken(String tenantCode, String email, String password) throws Exception {
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
