package com.course_aura_hub.auth.service;

import com.course_aura_hub.auth.config.AuthSecurityProperties;
import com.course_aura_hub.auth.repository.OneTimeTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Deletes expired one-time tokens and refresh sessions so the tables stay bounded.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthMaintenanceJob {

    private final OneTimeTokenRepository oneTimeTokenRepository;
    private final RefreshSessionService refreshSessionService;
    private final AuthSecurityProperties properties;

    @Scheduled(cron = "${app.security.auth.cleanup-cron:0 17 3 * * *}")
    @Transactional
    public void purgeExpiredCredentials() {
        int tokens = oneTimeTokenRepository.deleteExpiredBefore(Instant.now().minus(properties.tokenRetention()));
        int sessions = refreshSessionService.purgeExpired();
        log.info("Auth maintenance removed {} expired one-time tokens and {} expired refresh tokens", tokens, sessions);
    }
}
