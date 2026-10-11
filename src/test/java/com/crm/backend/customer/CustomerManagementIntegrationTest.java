package com.crm.backend.customer;

import com.crm.backend.auth.AuthResponse;
import com.crm.backend.auth.LoginRequest;
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

import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CustomerManagementIntegrationTest {

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
    @DisplayName("Admin and Sales Rep can both create customers successfully")
    void testBothRolesCanCreateCustomers() throws Exception {
        Tenant tenant = registerTenant("cust-tenant", "Cust Tenant", "admin@cust.com", "AdminPass123");
        String adminToken = login("cust-tenant", "admin@cust.com", "AdminPass123");

        createTestUser(tenant.getId(), "rep@cust.com", "Sales", "Rep", Role.SALES_REP, "RepPass123");
        String repToken = login("cust-tenant", "rep@cust.com", "RepPass123");

        // 1. Admin creates customer
        CreateCustomerRequest adminRequest = new CreateCustomerRequest(
                "Acme",
                "Corporation",
                null,
                "billing@acme.com",
                "+1-800-555-0100",
                "Acme Global",
                "100 Enterprise Way",
                "Tier 1 Enterprise Client"
        );

        mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adminRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.tenantId").value(tenant.getId()))
                .andExpect(jsonPath("$.firstName").value("Acme"))
                .andExpect(jsonPath("$.lastName").value("Corporation"))
                .andExpect(jsonPath("$.name").value("Acme Corporation"))
                .andExpect(jsonPath("$.email").value("billing@acme.com"))
                .andExpect(jsonPath("$.phone").value("+1-800-555-0100"))
                .andExpect(jsonPath("$.company").value("Acme Global"))
                .andExpect(jsonPath("$.address").value("100 Enterprise Way"))
                .andExpect(jsonPath("$.notes").value("Tier 1 Enterprise Client"))
                .andExpect(jsonPath("$.isActive").value(true))
                .andExpect(jsonPath("$.active").value(true));

        // 2. Sales Rep creates customer using flexible name field
        CreateCustomerRequest repRequest = new CreateCustomerRequest(
                null,
                null,
                "Wayne Enterprises",
                "bruce@wayne.com",
                "+1-555-0199",
                "Wayne Corp",
                "Gotham City",
                "High Value Account"
        );

        mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", "Bearer " + repToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(repRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.tenantId").value(tenant.getId()))
                .andExpect(jsonPath("$.firstName").value("Wayne"))
                .andExpect(jsonPath("$.lastName").value("Enterprises"))
                .andExpect(jsonPath("$.email").value("bruce@wayne.com"))
                .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    @DisplayName("Retrieve customer by ID within tenant")
    void testRetrieveCustomerById() throws Exception {
        Tenant tenant = registerTenant("retrieve-tenant", "Retrieve Co", "admin@ret.com", "Pass12345");
        String token = login("retrieve-tenant", "admin@ret.com", "Pass12345");

        Customer customer = createCustomer(tenant.getId(), "Stark", "Industries", "tony@stark.com", "Stark Corp");

        mockMvc.perform(get("/api/v1/customers/" + customer.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(customer.getId()))
                .andExpect(jsonPath("$.tenantId").value(tenant.getId()))
                .andExpect(jsonPath("$.firstName").value("Stark"))
                .andExpect(jsonPath("$.lastName").value("Industries"))
                .andExpect(jsonPath("$.email").value("tony@stark.com"))
                .andExpect(jsonPath("$.company").value("Stark Corp"))
                .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    @DisplayName("List customers with tenant scoping and pagination")
    void testListCustomersTenantScoped() throws Exception {
        Tenant tenantA = registerTenant("list-tenant-a", "Tenant A", "admin@a.com", "Pass12345");
        String tokenA = login("list-tenant-a", "admin@a.com", "Pass12345");

        Tenant tenantB = registerTenant("list-tenant-b", "Tenant B", "admin@b.com", "Pass12345");

        // Tenant A gets 3 customers
        createCustomer(tenantA.getId(), "Alpha", "One", "alpha1@a.com", "A1 Corp");
        createCustomer(tenantA.getId(), "Alpha", "Two", "alpha2@a.com", "A2 Corp");
        createCustomer(tenantA.getId(), "Alpha", "Three", "alpha3@a.com", "A3 Corp");

        // Tenant B gets 2 customers
        createCustomer(tenantB.getId(), "Beta", "One", "beta1@b.com", "B1 Corp");
        createCustomer(tenantB.getId(), "Beta", "Two", "beta2@b.com", "B2 Corp");

        // Query Tenant A customers
        mockMvc.perform(get("/api/v1/customers?page=0&size=10")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.content[*].tenantId", everyItem(is(tenantA.getId().intValue()))));

        // Test pagination: page 0, size 2
        mockMvc.perform(get("/api/v1/customers?page=0&size=2")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content", hasSize(2)));

        // Test pagination: page 1, size 2
        mockMvc.perform(get("/api/v1/customers?page=1&size=2")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    @DisplayName("Search customers by name, email or company")
    void testSearchCustomers() throws Exception {
        Tenant tenant = registerTenant("search-tenant", "Search Co", "admin@search.com", "Pass12345");
        String token = login("search-tenant", "admin@search.com", "Pass12345");

        createCustomer(tenant.getId(), "Cyberdyne", "Systems", "contact@cyberdyne.com", "Cyberdyne Global");
        createCustomer(tenant.getId(), "Miles", "Dyson", "miles@personal.com", "Skynet Tech");
        createCustomer(tenant.getId(), "John", "Connor", "resistance@future.org", "Tech Com");

        // 1. Search by company
        mockMvc.perform(get("/api/v1/customers?search=Cyberdyne")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].company").value("Cyberdyne Global"));

        // 2. Search by email domain
        mockMvc.perform(get("/api/v1/customers?query=future.org")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value("resistance@future.org"));

        // 3. Search by name
        mockMvc.perform(get("/api/v1/customers?q=Dyson")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].firstName").value("Miles"));
    }

    @Test
    @DisplayName("Safe sorting on customer listing")
    void testSafeSortingOnCustomerListing() throws Exception {
        Tenant tenant = registerTenant("sort-cust-tenant", "Sort Co", "admin@sortc.com", "Pass12345");
        String token = login("sort-cust-tenant", "admin@sortc.com", "Pass12345");

        createCustomer(tenant.getId(), "Charlie", "Brown", "charlie@sort.com", "Peanuts");
        createCustomer(tenant.getId(), "Alice", "Smith", "alice@sort.com", "Wonderland");

        // Valid sort by firstName asc
        mockMvc.perform(get("/api/v1/customers?sort=firstName,asc")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].firstName").value("Alice"))
                .andExpect(jsonPath("$.content[1].firstName").value("Charlie"));

        // Invalid sort property returns 400 Bad Request
        mockMvc.perform(get("/api/v1/customers?sort=invalidField,asc")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Invalid sort property")));
    }

    @Test
    @DisplayName("Update customer details within tenant")
    void testUpdateCustomerSuccessfully() throws Exception {
        Tenant tenant = registerTenant("up-tenant", "Update Co", "admin@up.com", "Pass12345");
        String token = login("up-tenant", "admin@up.com", "Pass12345");

        Customer customer = createCustomer(tenant.getId(), "OldFirst", "OldLast", "old@up.com", "Old Co");

        UpdateCustomerRequest updateRequest = new UpdateCustomerRequest(
                "NewFirst",
                "NewLast",
                null,
                "new@up.com",
                "+1-999-888-7777",
                "New Co",
                "777 Innovation Blvd",
                "Upgraded contract"
        );

        mockMvc.perform(put("/api/v1/customers/" + customer.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(customer.getId()))
                .andExpect(jsonPath("$.firstName").value("NewFirst"))
                .andExpect(jsonPath("$.lastName").value("NewLast"))
                .andExpect(jsonPath("$.email").value("new@up.com"))
                .andExpect(jsonPath("$.company").value("New Co"))
                .andExpect(jsonPath("$.address").value("777 Innovation Blvd"))
                .andExpect(jsonPath("$.notes").value("Upgraded contract"));

        Customer updated = customerRepository.findById(customer.getId()).orElseThrow();
        assertEquals("NewFirst", updated.getFirstName());
        assertEquals("new@up.com", updated.getEmail());
    }

    @Test
    @DisplayName("Soft delete customer and verify exclusion from queries")
    void testSoftDeleteCustomer() throws Exception {
        Tenant tenant = registerTenant("del-tenant", "Delete Co", "admin@del.com", "Pass12345");
        String token = login("del-tenant", "admin@del.com", "Pass12345");

        Customer customer = createCustomer(tenant.getId(), "Temp", "Cust", "temp@del.com", "Temp Co");
        assertTrue(customer.isActive());

        // 1. DELETE /api/v1/customers/{id} returns 204 No Content
        mockMvc.perform(delete("/api/v1/customers/" + customer.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // Verify entity is soft-deleted in database
        Customer deletedFromDb = customerRepository.findById(customer.getId()).orElseThrow();
        assertFalse(deletedFromDb.isActive(), "Customer must be soft deleted (isActive = false)");

        // 2. GET /api/v1/customers/{id} returns 404 Not Found for soft-deleted customer
        mockMvc.perform(get("/api/v1/customers/" + customer.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("Customer not found with id: " + customer.getId())));

        // 3. GET /api/v1/customers listing excludes soft-deleted customer
        mockMvc.perform(get("/api/v1/customers")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    @DisplayName("Cross-tenant customer access (read, update, delete) is denied with 404")
    void testCrossTenantIsolation() throws Exception {
        Tenant tenantA = registerTenant("cross-a", "Cross A", "admin@crossa.com", "Pass12345");
        String tokenA = login("cross-a", "admin@crossa.com", "Pass12345");

        Tenant tenantB = registerTenant("cross-b", "Cross B", "admin@crossb.com", "Pass12345");
        Customer customerB = createCustomer(tenantB.getId(), "Target", "B", "target@b.com", "Target B Corp");

        // Tenant A admin tries to GET customer belonging to Tenant B -> 404
        mockMvc.perform(get("/api/v1/customers/" + customerB.getId())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("Customer not found with id: " + customerB.getId())));

        // Tenant A admin tries to PUT customer belonging to Tenant B -> 404
        UpdateCustomerRequest updateRequest = new UpdateCustomerRequest("Hacked", "B", null, "hacked@b.com", null, null, null, null);
        mockMvc.perform(put("/api/v1/customers/" + customerB.getId())
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isNotFound());

        // Tenant A admin tries to DELETE customer belonging to Tenant B -> 404
        mockMvc.perform(delete("/api/v1/customers/" + customerB.getId())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());

        // Verify customer B remains intact and active in DB
        Customer intactB = customerRepository.findById(customerB.getId()).orElseThrow();
        assertEquals(tenantB.getId(), intactB.getTenantId());
        assertTrue(intactB.isActive());
    }

    @Test
    @DisplayName("Unauthenticated requests return 401 Unauthorized")
    void testUnauthenticatedRequestsReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/customers"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("Validation rejects invalid inputs on customer creation")
    void testValidationRejectsInvalidInputs() throws Exception {
        Tenant tenant = registerTenant("val-cust", "Val Co", "admin@valc.com", "Pass12345");
        String token = login("val-cust", "admin@valc.com", "Pass12345");

        // Invalid email format
        CreateCustomerRequest invalidEmailRequest = new CreateCustomerRequest(
                "John", "Doe", null, "invalid-email-format", null, null, null, null
        );

        mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidEmailRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors.email").isNotEmpty());

        // Missing name completely
        CreateCustomerRequest missingNameRequest = new CreateCustomerRequest(
                "", "", "", "valid@email.com", null, null, null, null
        );

        mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(missingNameRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message", containsString("Customer first name (or name) is required")));
    }

    @Test
    @DisplayName("Prevent tenant reassignment through customer payload")
    void testPreventTenantReassignmentThroughPayload() throws Exception {
        Tenant tenantA = registerTenant("tamper-cust-a", "Tamper Co", "admin@tamper.com", "Pass12345");
        String token = login("tamper-cust-a", "admin@tamper.com", "Pass12345");

        Map<String, Object> spoofedPayload = Map.of(
                "tenantId", 999999L,
                "firstName", "Innocent",
                "lastName", "Customer",
                "email", "innocent@cust.com",
                "company", "Tamper Corp"
        );

        MvcResult result = mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(spoofedPayload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").value(tenantA.getId()))
                .andReturn();

        CustomerResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), CustomerResponse.class);
        Customer saved = customerRepository.findById(response.id()).orElseThrow();
        assertEquals(tenantA.getId(), saved.getTenantId());
        assertNotEquals(999999L, saved.getTenantId());
    }

    private Tenant registerTenant(String tenantCode, String companyName, String email, String password) {
        Tenant tenant = new Tenant(tenantCode, companyName);
        tenant = tenantRepository.save(tenant);

        User admin = new User(
                tenant.getId(),
                email,
                passwordEncoder.encode(password),
                "Admin",
                "User",
                Role.TENANT_ADMIN
        );
        userRepository.save(admin);
        return tenant;
    }

    private User createTestUser(Long tenantId, String email, String firstName, String lastName, Role role, String password) {
        User user = new User(
                tenantId,
                email,
                passwordEncoder.encode(password),
                firstName,
                lastName,
                role
        );
        return userRepository.save(user);
    }

    private Customer createCustomer(Long tenantId, String firstName, String lastName, String email, String company) {
        Customer customer = new Customer(tenantId, firstName, lastName, email, "+1-555-0100", company);
        return customerRepository.save(customer);
    }

    private String login(String tenantCode, String email, String password) throws Exception {
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
