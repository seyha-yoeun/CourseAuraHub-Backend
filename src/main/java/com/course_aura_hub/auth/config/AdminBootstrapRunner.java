package com.course_aura_hub.auth.config;

import com.course_aura_hub.auth.model.AuthProvider;
import com.course_aura_hub.auth.model.Role;
import com.course_aura_hub.auth.model.User;
import com.course_aura_hub.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Optional local/bootstrap mechanism for creating or promoting admin accounts.
 *
 * <p>Disabled unless explicit credentials or email addresses are provided through the
 * environment. This avoids shipping a hard-coded administrator account.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrapRunner implements ApplicationRunner {

    private final AdminBootstrapProperties properties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        createConfiguredAdmin();
        promoteConfiguredAdmins();
    }

    private void createConfiguredAdmin() {
        if (!StringUtils.hasText(properties.email()) || !StringUtils.hasText(properties.password())) {
            return;
        }

        String email = normalize(properties.email());
        User user = userRepository.findByEmail(email).orElseGet(() -> {
            User created = User.builder()
                    .firstName("Platform")
                    .lastName("Admin")
                    .email(email)
                    .password(passwordEncoder.encode(properties.password()))
                    .role(Role.ROLE_ADMIN)
                    .provider(AuthProvider.LOCAL)
                    .enabled(true)
                    // Configured by the operator, so there is no mailbox to verify
                    .emailVerified(true)
                    .emailVerifiedAt(Instant.now())
                    .build();
            log.info("Created bootstrap admin account email={}", email);
            return userRepository.save(created);
        });

        if (user.getRole() != Role.ROLE_ADMIN) {
            user.setRole(Role.ROLE_ADMIN);
            userRepository.save(user);
            log.info("Promoted bootstrap account to admin email={}", email);
        }
    }

    private void promoteConfiguredAdmins() {
        List<String> emails = properties.promoteEmails();
        for (String rawEmail : emails) {
            if (!StringUtils.hasText(rawEmail)) {
                continue;
            }
            String email = normalize(rawEmail);
            userRepository.findByEmail(email).ifPresentOrElse(user -> {
                if (user.getRole() != Role.ROLE_ADMIN) {
                    user.setRole(Role.ROLE_ADMIN);
                    userRepository.save(user);
                    log.info("Promoted configured user to admin email={}", email);
                }
            }, () -> log.warn("Configured admin email is not registered yet email={}", email));
        }
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
