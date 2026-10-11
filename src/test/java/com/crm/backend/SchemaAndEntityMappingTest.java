package com.crm.backend;

import com.crm.backend.customer.Customer;
import com.crm.backend.lead.Lead;
import com.crm.backend.lead.LeadStatus;
import com.crm.backend.tenant.Tenant;
import com.crm.backend.user.Role;
import com.crm.backend.user.User;
import jakarta.persistence.EntityManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class SchemaAndEntityMappingTest {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("Verify Flyway migration executed and created all four CRM tables")
    void verifyFlywayMigrationAndTables() {
        assertNotNull(flyway, "Flyway bean should be injected");
        assertNotNull(flyway.info().current(), "Flyway current migration should exist");
        assertEquals("1", flyway.info().current().getVersion().getVersion(), "Flyway migration version should be 1");

        List<String> tableNames = jdbcTemplate.query(
                "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC'",
                (rs, rowNum) -> rs.getString("TABLE_NAME").toLowerCase()
        );

        assertTrue(tableNames.contains("tenants"), "Table 'tenants' must exist");
        assertTrue(tableNames.contains("users"), "Table 'users' must exist");
        assertTrue(tableNames.contains("customers"), "Table 'customers' must exist");
        assertTrue(tableNames.contains("leads"), "Table 'leads' must exist");
        assertTrue(tableNames.contains("flyway_schema_history"), "Table 'flyway_schema_history' must exist");
    }

    @Test
    @DisplayName("Verify JPA entities mapping, lifecycle timestamps, and persistence")
    void verifyJpaEntityPersistence() {
        // 1. Create and persist Tenant
        Tenant tenant = new Tenant("cypso-tech", "Cypso Technologies");
        entityManager.persist(tenant);
        entityManager.flush();

        assertNotNull(tenant.getId(), "Tenant ID should be generated");
        assertNotNull(tenant.getCreatedAt(), "Tenant createdAt should be auto-populated");
        assertNotNull(tenant.getUpdatedAt(), "Tenant updatedAt should be auto-populated");
        assertEquals("cypso-tech", tenant.getTenantCode());

        // 2. Create and persist User linked to Tenant
        User user = new User(tenant.getId(), "admin@cypso.com", "hashed_pwd", "Alice", "Admin", Role.TENANT_ADMIN);
        entityManager.persist(user);
        entityManager.flush();

        assertNotNull(user.getId(), "User ID should be generated");
        assertEquals(tenant.getId(), user.getTenantId());
        assertEquals("admin@cypso.com", user.getEmail());
        assertTrue(user.isActive());

        // 3. Create and persist Customer linked to Tenant
        Customer customer = new Customer(tenant.getId(), "Bob", "Smith", "bob@example.com", "+1234567890", "Acme Inc");
        entityManager.persist(customer);
        entityManager.flush();

        assertNotNull(customer.getId(), "Customer ID should be generated");
        assertEquals(tenant.getId(), customer.getTenantId());
        assertTrue(customer.isActive());

        // 4. Create and persist Lead assigned to User
        Lead lead = new Lead(tenant.getId(), "Charlie", "Brown", "charlie@example.com", "+9876543210", "Brown Corp");
        lead.assignTo(user.getId());
        entityManager.persist(lead);
        entityManager.flush();

        assertNotNull(lead.getId(), "Lead ID should be generated");
        assertEquals(tenant.getId(), lead.getTenantId());
        assertEquals(user.getId(), lead.getAssignedToUserId());
        assertEquals(LeadStatus.NEW, lead.getStatus());

        // 5. Convert Lead to Customer
        lead.convertToCustomer(customer.getId());
        entityManager.flush();

        assertEquals(LeadStatus.CONVERTED, lead.getStatus());
        assertEquals(customer.getId(), lead.getConvertedCustomerId());
        assertNotNull(lead.getConvertedAt(), "Converted timestamp should be populated");
    }
}
