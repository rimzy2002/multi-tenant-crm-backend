package com.crm.backend.user;

public enum Role {
    TENANT_ADMIN,
    SALES_REP;

    public String getAuthority() {
        return "ROLE_" + name();
    }
}
