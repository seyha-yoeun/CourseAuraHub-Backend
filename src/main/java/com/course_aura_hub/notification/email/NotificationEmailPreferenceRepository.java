package com.course_aura_hub.notification.email;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface NotificationEmailPreferenceRepository
        extends JpaRepository<NotificationEmailPreference, NotificationEmailPreference.Id> {

    List<NotificationEmailPreference> findByIdUserId(UUID userId);

    List<NotificationEmailPreference> findByIdUserIdIn(Collection<UUID> userIds);
}
