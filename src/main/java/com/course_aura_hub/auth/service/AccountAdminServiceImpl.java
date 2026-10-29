package com.course_aura_hub.auth.service;

import com.course_aura_hub.auth.dto.AdminUserStatusResponse;
import com.course_aura_hub.auth.exception.AuthException;
import com.course_aura_hub.auth.model.AuditEventType;
import com.course_aura_hub.auth.model.SessionRevocationReason;
import com.course_aura_hub.auth.model.User;
import com.course_aura_hub.auth.repository.UserRepository;
import com.course_aura_hub.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountAdminServiceImpl implements AccountAdminService {

    private final UserRepository userRepository;
    private final RefreshSessionService refreshSessionService;
    private final AuthAuditService auditService;

    @Override
    @Transactional(readOnly = true)
    public AdminUserStatusResponse getStatus(UUID userId) {
        return toResponse(userRepository.findById(userId).orElseThrow(AccountAdminServiceImpl::notFound));
    }

    @Override
    @Transactional
    public AdminUserStatusResponse setEnabled(UUID actorId, UUID userId, boolean enabled) {
        if (actorId.equals(userId) && !enabled) {
            throw new AuthException("Administrators cannot disable their own account");
        }
        User user = userRepository.findByIdForUpdate(userId).orElseThrow(AccountAdminServiceImpl::notFound);
        if (user.isEnabled() == enabled) {
            return toResponse(user);
        }
        user.setEnabled(enabled);
        if (!enabled) {
            user.setTokenVersion(user.getTokenVersion() + 1);
        }
        userRepository.save(user);
        if (!enabled) {
            refreshSessionService.revokeAllSessions(userId, SessionRevocationReason.ACCOUNT_DISABLED);
        }
        log.info("Admin {} set enabled={} for account {}", actorId, enabled, user.getEmail());
        auditService.record(AuthAuditEntry.success(AuditEventType.ACCOUNT_STATUS_CHANGED, user)
                .reason(enabled ? "ENABLED" : "DISABLED")
                .metadata(Map.of("actorId", actorId.toString()))
                .build());
        return toResponse(user);
    }

    @Override
    @Transactional
    public AdminUserStatusResponse unlock(UUID actorId, UUID userId) {
        User user = userRepository.findByIdForUpdate(userId).orElseThrow(AccountAdminServiceImpl::notFound);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);
        auditService.record(AuthAuditEntry.success(AuditEventType.ACCOUNT_UNLOCKED, user)
                .metadata(Map.of("actorId", actorId.toString()))
                .build());
        return toResponse(user);
    }

    @Override
    @Transactional
    public int revokeAllSessions(UUID actorId, UUID userId) {
        User user = userRepository.findByIdForUpdate(userId).orElseThrow(AccountAdminServiceImpl::notFound);
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);
        log.info("Admin {} revoked all sessions for account {}", actorId, user.getEmail());
        return refreshSessionService.revokeAllSessions(userId, SessionRevocationReason.ADMIN_REVOKED);
    }

    private static AdminUserStatusResponse toResponse(User user) {
        return new AdminUserStatusResponse(
                user.getId(),
                user.getEmail(),
                user.isEnabled(),
                user.isEmailVerified(),
                user.isMfaEnabled(),
                user.getLockedUntil(),
                user.getFailedLoginAttempts()
        );
    }

    private static ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("User not found");
    }
}
