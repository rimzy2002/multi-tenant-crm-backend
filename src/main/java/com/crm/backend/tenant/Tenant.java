package com.crm.backend.tenant;

import com.crm.backend.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "tenants")
public class Tenant extends BaseEntity {

    @Column(name = "tenant_code", nullable = false, unique = true, length = 50)
    private String tenantCode;

    @Column(name = "company_name", nullable = false, length = 100)
    private String companyName;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    public Tenant() {
    }

    public Tenant(String tenantCode, String companyName) {
        this.tenantCode = tenantCode;
        this.companyName = companyName;
        this.isActive = true;
    }

    @PrePersist
    @PreUpdate
    private void normalizeTenantCode() {
        if (this.tenantCode != null) {
            this.tenantCode = this.tenantCode.trim().toLowerCase();
        }
    }

    public String getTenantCode() {
        return tenantCode;
    }

    public void setTenantCode(String tenantCode) {
        this.tenantCode = tenantCode;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }
}
