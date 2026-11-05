package com.course_aura_hub.notification.email;

import com.course_aura_hub.common.email.EmailDeliveryException;
import com.course_aura_hub.common.email.EmailSender;
import com.course_aura_hub.notification.dto.NotificationResponse;
import com.course_aura_hub.notification.model.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Sends notification emails: instant ones as they are created, the rest in a daily digest. Only verified, enabled
 * accounts get mail. Rows are stamped with emailed_at once sent, so nothing is mailed twice.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEmailDelivery {

    private static final int DIGEST_WINDOW_DAYS = 7;

    private final JdbcTemplate jdbcTemplate;
    private final EmailSender emailSender;
    private final NotificationEmailRenderer renderer;
    private final NotificationEmailService preferences;
    private final NotificationEmailProperties properties;

    private record Recipient(UUID id, String email, String firstName) {
    }

    private record Pending(long id, UUID userId, String email, String firstName, NotificationType type,
                           NotificationEmailRenderer.Item item) {
    }

    /** Emails the instant-mode notifications among these freshly committed ones. */
    public void sendInstant(Map<UUID, NotificationResponse> byUser) {
        if (!properties.enabled() || byUser.isEmpty()) {
            return;
        }
        Map<UUID, Map<NotificationType, EmailMode>> chosen = preferences.chosenModes(byUser.keySet());
        for (Recipient recipient : recipients(byUser.keySet())) {
            NotificationResponse notification = byUser.get(recipient.id());
            NotificationType type = NotificationType.valueOf(notification.type().toUpperCase(Locale.ROOT));
            if (mode(chosen, recipient.id(), type) != EmailMode.INSTANT) {
                continue;
            }
            NotificationEmailRenderer.Item item =
                    new NotificationEmailRenderer.Item(notification.title(), notification.message(), notification.link());
            if (send(renderer.single(recipient.email(), recipient.firstName(), item,
                    preferences.unsubscribeUrl(recipient.id(), type)), recipient.id())) {
                markEmailed(List.of(notification.id()));
            }
        }
    }

    /** One email per person with their unread digest-mode notifications from the last week. Returns emails sent. */
    public int sendDigests() {
        if (!properties.enabled()) {
            return 0;
        }
        Map<UUID, List<Pending>> byUser = new LinkedHashMap<>();
        jdbcTemplate.query("""
                select n.id, n.user_id, u.email, u.first_name, n.type, n.title, n.message, n.link
                from notifications n
                join users u on u.id = n.user_id
                where n.read_at is null and n.emailed_at is null and n.created_at > ?
                  and u.enabled and u.email_verified
                order by n.user_id, n.created_at desc, n.id desc
                """, rs -> {
            NotificationType type;
            try {
                type = NotificationType.valueOf(rs.getString("type"));
            } catch (IllegalArgumentException ex) {
                return;
            }
            UUID userId = rs.getObject("user_id", UUID.class);
            byUser.computeIfAbsent(userId, id -> new ArrayList<>()).add(new Pending(
                    rs.getLong("id"), userId, rs.getString("email"), rs.getString("first_name"), type,
                    new NotificationEmailRenderer.Item(rs.getString("title"), rs.getString("message"), rs.getString("link"))));
        }, Timestamp.from(java.time.Instant.now().minus(java.time.Duration.ofDays(DIGEST_WINDOW_DAYS))));

        Map<UUID, Map<NotificationType, EmailMode>> chosen = preferences.chosenModes(byUser.keySet());
        int sent = 0;
        for (Map.Entry<UUID, List<Pending>> entry : byUser.entrySet()) {
            List<Pending> digest = entry.getValue().stream()
                    .filter(pending -> mode(chosen, entry.getKey(), pending.type()) == EmailMode.DIGEST)
                    .toList();
            if (digest.isEmpty()) {
                continue;
            }
            Pending first = digest.getFirst();
            List<NotificationEmailRenderer.Item> shown = digest.stream()
                    .limit(properties.digestMaxItems()).map(Pending::item).toList();
            boolean delivered = send(renderer.digest(first.email(), first.firstName(), shown, digest.size() - shown.size(),
                    preferences.unsubscribeUrl(entry.getKey(), null)), entry.getKey());
            if (delivered) {
                // Everything considered is marked, so a long backlog doesn't come back tomorrow
                markEmailed(digest.stream().map(Pending::id).toList());
                sent++;
            }
        }
        log.info("Sent {} notification digests", sent);
        return sent;
    }

    private List<Recipient> recipients(Collection<UUID> userIds) {
        String placeholders = String.join(",", java.util.Collections.nCopies(userIds.size(), "?"));
        return jdbcTemplate.query(
                "select id, email, first_name from users where enabled and email_verified and id in (" + placeholders + ")",
                (rs, row) -> new Recipient(rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("first_name")),
                userIds.toArray());
    }

    private boolean send(com.course_aura_hub.common.email.EmailMessage message, UUID userId) {
        try {
            emailSender.send(message);
            return true;
        } catch (EmailDeliveryException ex) {
            // The notification stays in the feed; a digest-mode one is retried by the next digest
            log.warn("Notification email not delivered userId={}", userId);
            return false;
        }
    }

    private void markEmailed(List<Long> ids) {
        jdbcTemplate.batchUpdate("update notifications set emailed_at = now() where id = ?",
                ids.stream().map(id -> new Object[]{id}).toList());
    }

    private static EmailMode mode(Map<UUID, Map<NotificationType, EmailMode>> chosen, UUID userId, NotificationType type) {
        return chosen.getOrDefault(userId, Map.of()).getOrDefault(type, EmailMode.defaultFor(type));
    }
}
