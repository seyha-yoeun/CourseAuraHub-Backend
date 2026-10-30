package com.course_aura_hub.auth.service;

import com.course_aura_hub.auth.config.AuthSecurityProperties;
import com.course_aura_hub.auth.model.AuditEventType;
import com.course_aura_hub.auth.model.User;
import com.course_aura_hub.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Failed-attempt counting and temporary lockout, shared by password and second-factor checks.
 * Callers must hold the user's row lock.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private final UserRepository userRepository;
    private final AuthSecurityProperties properties;
    private final AuthAuditService auditService;

    /**
     * @return true when this failure locked the account
     */
    public boolean recordFailure(User user, Instant now) {
        int attempts = user.getFailedLoginAttempts() + 1;
        AuthSecurityProperties.Lockout lockout = properties.lockout();
        boolean locked = attempts >= lockout.maxFailedAttempts();
        if (locked) {
            user.setLockedUntil(now.plus(lockout.lockDuration()));
            // Start a fresh window once the lock expires instead of re-locking on the next mistake
            user.setFailedLoginAttempts(0);
            log.warn("Account locked for {} after repeated failed sign-in attempts: {}", lockout.lockDuration(), user.getEmail());
            auditService.record(AuthAuditEntry.success(AuditEventType.ACCOUNT_LOCKED, user)
                    .reason("TOO_MANY_FAILED_ATTEMPTS").build());
        } else {
            user.setFailedLoginAttempts(attempts);
        }
        userRepository.save(user);
        return locked;
    }

    public void recordSuccess(User user) {
        if (user.getFailedLoginAttempts() > 0 || user.getLockedUntil() != null) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            userRepository.save(user);
        }
    }
}
