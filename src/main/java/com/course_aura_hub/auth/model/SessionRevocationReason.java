package com.course_aura_hub.auth.model;

public enum SessionRevocationReason {
    /** Replaced by a newer refresh token in the same family (normal rotation). */
    ROTATED,
    LOGOUT,
    LOGOUT_ALL,
    REVOKED_BY_USER,
    PASSWORD_CHANGED,
    PASSWORD_RESET,
    ACCOUNT_DISABLED,
    ADMIN_REVOKED,
    REUSE_DETECTED,
    SESSION_LIMIT
}
