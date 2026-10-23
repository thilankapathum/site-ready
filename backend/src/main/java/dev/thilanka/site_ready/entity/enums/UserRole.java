package dev.thilanka.site_ready.entity.enums;

public enum UserRole {
    VENDOR, ENGINEER, MANAGER, ADMIN, VENDOR_MANAGER;

    /** VENDOR_MANAGER is a superset of VENDOR, scoped to the same company. */
    public boolean isVendorSide() {
        return this == VENDOR || this == VENDOR_MANAGER;
    }
}
