package com.crm.backend;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class MySQLRealDatabaseVerificationTest {

    private static final Logger log = LoggerFactory.getLogger(MySQLRealDatabaseVerificationTest.class);

    private static final String DEFAULT_URL = "jdbc:mysql://localhost:3306/multi_tenant_crm";
    private static final String DEFAULT_USERNAME = "crm_app";

    @Test
    @DisplayName("Verify real MySQL migration, tables, foreign keys, and constraints")
    void verifyMySQLMigrationAndConstraints() throws Exception {
        String url = resolveProperty("DB_URL", DEFAULT_URL);
        String username = resolveProperty("DB_USERNAME", DEFAULT_USERNAME);
        String password = resolveProperty("DB_PASSWORD", null);

        // Skip test gracefully if password is not configured in current environment
        assumeTrue(password != null && !password.trim().isEmpty(),
                "DB_PASSWORD is not configured in this process environment. Skipping real MySQL execution.");

        log.info("Connecting to real MySQL database at: {}", url);
        log.info("Authenticating with username: {}", username);

        // 1. Execute Flyway migration directly against the target MySQL database
        Flyway flyway = Flyway.configure()
                .dataSource(url, username, password)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load();

        MigrateResult result = flyway.migrate();
        log.info("Flyway migration completed successfully. Applied migrations: {}", result.migrationsExecuted);
        assertTrue(result.success, "Flyway migration must succeed");

        // 2. Verify connection and inspect database metadata
        try (Connection connection = DriverManager.getConnection(url, username, password);
             Statement statement = connection.createStatement()) {

            assertTrue(connection.isValid(5), "Connection to MySQL should be valid");

            // Verify database and user
            try (ResultSet rs = statement.executeQuery("SELECT DATABASE(), CURRENT_USER(), VERSION()")) {
                assertTrue(rs.next());
                log.info("Connected Database: {}", rs.getString(1));
                log.info("Connected User: {}", rs.getString(2));
                log.info("MySQL Version: {}", rs.getString(3));
                assertEquals("multi_tenant_crm", rs.getString(1));
            }

            // Verify tables exist
            List<String> tables = new ArrayList<>();
            try (ResultSet rs = statement.executeQuery("SHOW TABLES")) {
                while (rs.next()) {
                    tables.add(rs.getString(1).toLowerCase());
                }
            }
            log.info("Tables found in multi_tenant_crm: {}", tables);
            assertTrue(tables.contains("tenants"), "Table 'tenants' must exist in MySQL");
            assertTrue(tables.contains("users"), "Table 'users' must exist in MySQL");
            assertTrue(tables.contains("customers"), "Table 'customers' must exist in MySQL");
            assertTrue(tables.contains("leads"), "Table 'leads' must exist in MySQL");
            assertTrue(tables.contains("flyway_schema_history"), "Table 'flyway_schema_history' must exist in MySQL");

            // Verify foreign key constraints in information_schema
            List<String> foreignKeys = new ArrayList<>();
            String fkQuery = "SELECT CONSTRAINT_NAME FROM information_schema.TABLE_CONSTRAINTS " +
                    "WHERE CONSTRAINT_SCHEMA = 'multi_tenant_crm' AND CONSTRAINT_TYPE = 'FOREIGN KEY'";
            try (ResultSet rs = statement.executeQuery(fkQuery)) {
                while (rs.next()) {
                    foreignKeys.add(rs.getString(1).toLowerCase());
                }
            }
            log.info("Foreign keys in multi_tenant_crm: {}", foreignKeys);
            assertTrue(foreignKeys.contains("fk_users_tenant"), "FK fk_users_tenant must exist");
            assertTrue(foreignKeys.contains("fk_customers_tenant"), "FK fk_customers_tenant must exist");
            assertTrue(foreignKeys.contains("fk_leads_tenant"), "FK fk_leads_tenant must exist");
            assertTrue(foreignKeys.contains("fk_leads_assigned_user"), "FK fk_leads_assigned_user must exist");
            assertTrue(foreignKeys.contains("fk_leads_converted_customer"), "FK fk_leads_converted_customer must exist");

            // Verify unique constraints
            List<String> uniqueKeys = new ArrayList<>();
            String ukQuery = "SELECT CONSTRAINT_NAME FROM information_schema.TABLE_CONSTRAINTS " +
                    "WHERE CONSTRAINT_SCHEMA = 'multi_tenant_crm' AND CONSTRAINT_TYPE = 'UNIQUE'";
            try (ResultSet rs = statement.executeQuery(ukQuery)) {
                while (rs.next()) {
                    uniqueKeys.add(rs.getString(1).toLowerCase());
                }
            }
            log.info("Unique constraints in multi_tenant_crm: {}", uniqueKeys);
            assertTrue(uniqueKeys.contains("uk_tenants_tenant_code"), "UK uk_tenants_tenant_code must exist");
            assertTrue(uniqueKeys.contains("uk_users_tenant_email"), "UK uk_users_tenant_email must exist");
            assertTrue(uniqueKeys.contains("uk_users_tenant_id_id"), "UK uk_users_tenant_id_id must exist");
            assertTrue(uniqueKeys.contains("uk_customers_tenant_id_id"), "UK uk_customers_tenant_id_id must exist");
        }
    }

    private String resolveProperty(String key, String fallback) {
        String val = System.getenv(key);
        if (val != null && !val.trim().isEmpty()) {
            return val;
        }

        val = System.getProperty(key);
        if (val != null && !val.trim().isEmpty()) {
            return val;
        }

        File envFile = new File(".env");
        if (envFile.exists() && envFile.isFile()) {
            try (FileInputStream fis = new FileInputStream(envFile)) {
                Properties props = new Properties();
                props.load(fis);
                val = props.getProperty(key);
                if (val != null && !val.trim().isEmpty()) {
                    return val;
                }
            } catch (Exception ignored) {
            }
        }

        return fallback;
    }
}
