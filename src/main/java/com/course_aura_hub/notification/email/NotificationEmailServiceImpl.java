package com.course_aura_hub.notification.email;

import com.course_aura_hub.auth.security.TokenHasher;
import com.course_aura_hub.common.exception.BusinessRuleException;
import com.course_aura_hub.notification.model.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationEmailServiceImpl implements NotificationEmailService {

    static final String ALL = "all";
    private static final String TOKEN_PURPOSE = "email-unsubscribe";

    private final NotificationEmailPreferenceRepository repository;
    private final NotificationEmailProperties properties;
    private final TokenHasher tokenHasher;

    @Override
    @Transactional(readOnly = true)
    public List<EmailPreferenceDtos.Preference> findMine(UUID userId) {
        Map<NotificationType, EmailMode> chosen = chosenModes(List.of(userId)).getOrDefault(userId, Map.of());
        return Arrays.stream(NotificationType.values())
                .map(type -> new EmailPreferenceDtos.Preference(
                        type.apiValue(),
                        chosen.getOrDefault(type, EmailMode.defaultFor(type)).apiValue(),
                        EmailMode.defaultFor(type).apiValue()))
                .toList();
    }

    @Override
    @Transactional
    public List<EmailPreferenceDtos.Preference> update(UUID userId, List<EmailPreferenceDtos.Change> changes) {
        for (EmailPreferenceDtos.Change change : changes) {
            set(userId, parseType(change.type()), EmailMode.fromValue(change.mode()));
        }
        return findMine(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Map<NotificationType, EmailMode>> chosenModes(Collection<UUID> userIds) {
        Map<UUID, Map<NotificationType, EmailMode>> modes = new HashMap<>();
        if (userIds.isEmpty()) {
            return modes;
        }
        for (NotificationEmailPreference preference : repository.findByIdUserIdIn(userIds)) {
            modes.computeIfAbsent(preference.getId().getUserId(), id -> new HashMap<>())
                    .put(preference.getId().getType(), preference.getMode());
        }
        return modes;
    }

    @Override
    public String unsubscribeUrl(UUID userId, NotificationType type) {
        String scope = type == null ? ALL : type.apiValue();
        return UriComponentsBuilder.fromUriString(properties.apiUrl().replaceAll("/+$", ""))
                .path("/api/v1/notifications/unsubscribe")
                .queryParam("user", userId)
                .queryParam("scope", scope)
                .queryParam("token", tokenHasher.hash(TOKEN_PURPOSE, userId.toString(), scope))
                .build()
                .toUriString();
    }

    @Override
    @Transactional
    public boolean unsubscribe(UUID userId, String scope, String token) {
        String normalized = scope == null ? ALL : scope.trim().toLowerCase(Locale.ROOT);
        if (token == null || !tokenHasher.matches(token, TOKEN_PURPOSE, userId.toString(), normalized)) {
            return false;
        }
        if (ALL.equals(normalized)) {
            Arrays.stream(NotificationType.values()).forEach(type -> set(userId, type, EmailMode.OFF));
        } else {
            set(userId, parseType(normalized), EmailMode.OFF);
        }
        log.info("Notification emails turned off by link userId={} scope={}", userId, normalized);
        return true;
    }

    private void set(UUID userId, NotificationType type, EmailMode mode) {
        NotificationEmailPreference.Id id = new NotificationEmailPreference.Id(userId, type);
        NotificationEmailPreference preference = repository.findById(id)
                .orElseGet(() -> NotificationEmailPreference.builder().id(id).build());
        preference.setMode(mode);
        repository.save(preference);
    }

    private static NotificationType parseType(String value) {
        try {
            return NotificationType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            throw new BusinessRuleException("Unknown notification type: " + value);
        }
    }
}
