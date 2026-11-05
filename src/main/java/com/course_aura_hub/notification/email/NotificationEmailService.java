package com.course_aura_hub.notification.email;

import com.course_aura_hub.notification.model.NotificationType;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface NotificationEmailService {

    /** Every notification type with the user's mode and the default. */
    List<EmailPreferenceDtos.Preference> findMine(UUID userId);

    List<EmailPreferenceDtos.Preference> update(UUID userId, List<EmailPreferenceDtos.Change> changes);

    /** The modes these users chose, by user and type; missing entries mean the default. */
    Map<UUID, Map<NotificationType, EmailMode>> chosenModes(Collection<UUID> userIds);

    /** A link that turns off one type (or every type when {@code type} is null) without signing in. */
    String unsubscribeUrl(UUID userId, NotificationType type);

    /** Applies an unsubscribe link. Returns false when the token doesn't match. */
    boolean unsubscribe(UUID userId, String scope, String token);
}
