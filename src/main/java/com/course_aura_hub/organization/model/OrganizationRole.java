package com.course_aura_hub.organization.model;

public enum OrganizationRole {
    OWNER,
    MANAGER,
    MEMBER;

    public boolean canManage() {
        return this != MEMBER;
    }
}
