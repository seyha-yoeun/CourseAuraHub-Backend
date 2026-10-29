package com.course_aura_hub.auth.security;

/**
 * Conservatively collected request metadata for sessions and audit records.
 */
public record ClientInfo(String ipAddress, String userAgent) {

    public static final ClientInfo UNKNOWN = new ClientInfo(null, null);
}
