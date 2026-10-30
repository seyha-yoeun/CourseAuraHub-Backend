package com.course_aura_hub.auth.service;

import com.course_aura_hub.auth.dto.AdminUserStatusResponse;

import java.util.UUID;

/**
 * Administrative account-status changes. Every change is audited with the acting admin.
 */
public interface AccountAdminService {

    AdminUserStatusResponse getStatus(UUID userId);

    /** Disabling also revokes all sessions and outstanding access tokens. */
    AdminUserStatusResponse setEnabled(UUID actorId, UUID userId, boolean enabled);

    AdminUserStatusResponse unlock(UUID actorId, UUID userId);

    int revokeAllSessions(UUID actorId, UUID userId);
}
