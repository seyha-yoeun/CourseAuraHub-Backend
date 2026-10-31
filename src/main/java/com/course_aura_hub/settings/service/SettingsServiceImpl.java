package com.course_aura_hub.settings.service;

import com.course_aura_hub.auth.model.Role;
import com.course_aura_hub.auth.model.User;
import com.course_aura_hub.auth.repository.UserRepository;
import com.course_aura_hub.common.exception.ResourceNotFoundException;
import com.course_aura_hub.settings.dto.NotificationPreferences;
import com.course_aura_hub.settings.dto.PlatformSettingsResponse;
import com.course_aura_hub.settings.dto.ProfileResponse;
import com.course_aura_hub.settings.dto.ProfileUpdateRequest;
import com.course_aura_hub.settings.dto.PublicSettingsResponse;
import com.course_aura_hub.settings.dto.SettingsUpdateRequest;
import com.course_aura_hub.settings.model.PlatformSettings;
import com.course_aura_hub.settings.repository.PlatformSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettingsServiceImpl implements SettingsService {

    private static final short SETTINGS_ID = 1;
    private static final String DEFAULT_PLATFORM_NAME = "Course Aura Hub";
    private static final String DEFAULT_TAGLINE = "Learn. Build. Grow.";
    private static final String DEFAULT_CURRENCY = "USD";
    private static final String DEFAULT_LANGUAGE = "EN";

    private final PlatformSettingsRepository platformSettingsRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public PlatformSettingsResponse getSettings(String email) {
        User user = getUser(email);
        PlatformSettings settings = loadOrCreateSettings();
        return toResponse(settings, user);
    }

    @Override
    @Transactional(readOnly = true)
    public PublicSettingsResponse getPublicSettings() {
        PlatformSettings settings = platformSettingsRepository.findById(SETTINGS_ID)
                .orElseGet(this::defaultSettings);

        return new PublicSettingsResponse(
                settings.getPlatformName(),
                settings.getPlatformTagline(),
                settings.getCurrency(),
                settings.getDefaultLanguage()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isNewEnrollmentAlertEnabled() {
        return isAlertEnabled("newEnrollment");
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isCourseCompletionAlertEnabled() {
        return isAlertEnabled("courseCompletion");
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isWeeklyReportEnabled() {
        return isAlertEnabled("weeklyReport");
    }

    private boolean isAlertEnabled(String name) {
        PlatformSettings settings = platformSettingsRepository.findById(SETTINGS_ID)
                .orElseGet(this::defaultSettings);
        return Boolean.TRUE.equals(settings.getNotifications().get(name));
    }

    @Override
    @Transactional
    public PlatformSettingsResponse updateSettings(String email, SettingsUpdateRequest request) {
        User user = getUser(email);
        PlatformSettings settings = loadOrCreateSettings();

        if (request.platformName() != null) {
            settings.setPlatformName(request.platformName().trim());
        }
        if (request.platformTagline() != null) {
            settings.setPlatformTagline(request.platformTagline().trim());
        }
        if (request.currency() != null) {
            settings.setCurrency(request.currency().trim().toUpperCase(java.util.Locale.ROOT));
        }
        if (request.defaultLanguage() != null) {
            settings.setDefaultLanguage(request.defaultLanguage().trim());
        }
        if (request.notifications() != null) {
            settings.setNotifications(toNotificationMap(request.notifications()));
        }
        if (request.profile() != null) {
            applyProfile(user, request.profile());
            userRepository.save(user);
        }

        PlatformSettings saved = platformSettingsRepository.save(settings);
        log.info("Updated platform settings for user={}", user.getId());
        return toResponse(saved, user);
    }

    private PlatformSettings loadOrCreateSettings() {
        return platformSettingsRepository.findById(SETTINGS_ID)
                .orElseGet(() -> platformSettingsRepository.save(defaultSettings()));
    }

    private PlatformSettings defaultSettings() {
        Map<String, Boolean> notifications = new LinkedHashMap<>();
        notifications.put("newEnrollment", true);
        notifications.put("courseCompletion", true);
        notifications.put("weeklyReport", false);

        return PlatformSettings.builder()
                .id(SETTINGS_ID)
                .platformName(DEFAULT_PLATFORM_NAME)
                .platformTagline(DEFAULT_TAGLINE)
                .currency(DEFAULT_CURRENCY)
                .defaultLanguage(DEFAULT_LANGUAGE)
                .notifications(notifications)
                .profile(new LinkedHashMap<>())
                .build();
    }

    private PlatformSettingsResponse toResponse(PlatformSettings settings, User user) {
        return new PlatformSettingsResponse(
                settings.getPlatformName(),
                settings.getPlatformTagline(),
                settings.getCurrency(),
                settings.getDefaultLanguage(),
                toNotificationPreferences(settings.getNotifications()),
                toProfileResponse(user)
        );
    }

    private NotificationPreferences toNotificationPreferences(Map<String, Boolean> notifications) {
        Map<String, Boolean> safe = notifications == null ? Map.of() : notifications;
        return new NotificationPreferences(
                Boolean.TRUE.equals(safe.get("newEnrollment")),
                Boolean.TRUE.equals(safe.get("courseCompletion")),
                Boolean.TRUE.equals(safe.get("weeklyReport"))
        );
    }

    private Map<String, Boolean> toNotificationMap(NotificationPreferences preferences) {
        Map<String, Boolean> notifications = new LinkedHashMap<>();
        notifications.put("newEnrollment", preferences.newEnrollmentEnabled());
        notifications.put("courseCompletion", preferences.courseCompletionEnabled());
        notifications.put("weeklyReport", preferences.weeklyReportEnabled());
        return notifications;
    }

    private ProfileResponse toProfileResponse(User user) {
        return new ProfileResponse(
                fullName(user),
                user.getEmail(),
                displayRole(user.getRole()),
                defaultText(user.getImageUrl(), defaultAvatar(user)),
                defaultText(user.getBio(), ""),
                defaultText(user.getWebsite(), ""),
                defaultText(user.getTwitter(), "")
        );
    }

    private void applyProfile(User user, ProfileUpdateRequest profile) {
        if (StringUtils.hasText(profile.name())) {
            String[] parts = profile.name().trim().split("\\s+", 2);
            user.setFirstName(parts[0]);
            user.setLastName(parts.length > 1 ? parts[1] : "");
        }
        if (profile.avatar() != null) {
            user.setImageUrl(profile.avatar().trim());
        }
        if (profile.bio() != null) {
            user.setBio(profile.bio().trim());
        }
        if (profile.website() != null) {
            user.setWebsite(profile.website().trim());
        }
        if (profile.twitter() != null) {
            user.setTwitter(profile.twitter().trim());
        }
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> ResourceNotFoundException.of("User", email));
    }

    private String fullName(User user) {
        return (defaultText(user.getFirstName(), "") + " " + defaultText(user.getLastName(), "")).trim();
    }

    private String displayRole(Role role) {
        if (role == Role.ROLE_ADMIN) {
            return "Platform Admin";
        }
        if (role == Role.ROLE_INSTRUCTOR) {
            return "Instructor";
        }
        return "Student";
    }

    private String defaultAvatar(User user) {
        String seed = StringUtils.hasText(fullName(user)) ? fullName(user) : user.getEmail();
        return "https://api.dicebear.com/9.x/avataaars/svg?seed="
                + URLEncoder.encode(seed, StandardCharsets.UTF_8);
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
