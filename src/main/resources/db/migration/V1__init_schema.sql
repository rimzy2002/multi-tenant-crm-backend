-- ============================================================================
-- V1__init_schema.sql
-- Multi-Tenant CRM Initial Database Schema
-- ============================================================================

-- 1. Tenants Table
CREATE TABLE tenants (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_code VARCHAR(50) NOT NULL,
    company_name VARCHAR(100) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_tenants PRIMARY KEY (id),
    CONSTRAINT uk_tenants_tenant_code UNIQUE (tenant_code)
);

CREATE INDEX idx_tenants_is_active ON tenants (is_active);


-- 2. Users Table
CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    email VARCHAR(120) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(50) NOT NULL,
    last_name VARCHAR(50) NOT NULL,
    role VARCHAR(30) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT fk_users_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE RESTRICT,
    CONSTRAINT uk_users_tenant_email UNIQUE (tenant_id, email),
    CONSTRAINT uk_users_tenant_id_id UNIQUE (tenant_id, id)
);

CREATE INDEX idx_users_tenant_role ON users (tenant_id, role);
CREATE INDEX idx_users_tenant_active ON users (tenant_id, is_active);


-- 3. Customers Table
CREATE TABLE customers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    first_name VARCHAR(50) NOT NULL,
    last_name VARCHAR(50) NOT NULL,
    email VARCHAR(120) NULL,
    phone VARCHAR(30) NULL,
    company VARCHAR(100) NULL,
    address VARCHAR(255) NULL,
    notes TEXT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_customers PRIMARY KEY (id),
    CONSTRAINT fk_customers_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE RESTRICT,
    CONSTRAINT uk_customers_tenant_id_id UNIQUE (tenant_id, id)
);

CREATE INDEX idx_customers_tenant_name ON customers (tenant_id, last_name, first_name);
CREATE INDEX idx_customers_tenant_email ON customers (tenant_id, email);
CREATE INDEX idx_customers_tenant_company ON customers (tenant_id, company);
CREATE INDEX idx_customers_tenant_active ON customers (tenant_id, is_active);


-- 4. Leads Table
CREATE TABLE leads (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    first_name VARCHAR(50) NOT NULL,
    last_name VARCHAR(50) NOT NULL,
    email VARCHAR(120) NULL,
    phone VARCHAR(30) NULL,
    company VARCHAR(100) NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'NEW',
    assigned_to_user_id BIGINT NULL,
    converted_customer_id BIGINT NULL,
    converted_at TIMESTAMP NULL,
    notes TEXT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_leads PRIMARY KEY (id),
    CONSTRAINT fk_leads_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE RESTRICT,
    CONSTRAINT fk_leads_assigned_user FOREIGN KEY (tenant_id, assigned_to_user_id)
        REFERENCES users (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_leads_converted_customer FOREIGN KEY (tenant_id, converted_customer_id)
        REFERENCES customers (tenant_id, id) ON DELETE RESTRICT
);

CREATE INDEX idx_leads_tenant_status ON leads (tenant_id, status);
CREATE INDEX idx_leads_tenant_assigned ON leads (tenant_id, assigned_to_user_id);
CREATE INDEX idx_leads_tenant_name ON leads (tenant_id, last_name, first_name);
CREATE INDEX idx_leads_tenant_company ON leads (tenant_id, company);
CREATE INDEX idx_leads_tenant_active ON leads (tenant_id, is_active);
