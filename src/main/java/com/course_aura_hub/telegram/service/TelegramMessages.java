package com.course_aura_hub.telegram.service;

import com.course_aura_hub.notification.dto.NotificationResponse;
import com.course_aura_hub.telegram.client.TelegramClient;
import com.course_aura_hub.telegram.config.TelegramProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Turns notifications into Telegram messages. Every dynamic value goes through {@link #escape}. */
@Component
@RequiredArgsConstructor
public class TelegramMessages {

    private final TelegramProperties properties;

    public record Outgoing(String html, TelegramClient.Button button) {
    }

    public Outgoing notification(NotificationResponse notification) {
        String html = icon(notification.type()) + " <b>" + escape(notification.title()) + "</b>\n" + escape(notification.message());
        if (notification.link() == null) {
            return new Outgoing(html, null);
        }

        String url = absolute(notification.link());
        // Telegram rejects buttons that point at localhost, so development gets a plain link
        if (url.startsWith("https://")) {
            return new Outgoing(html, new TelegramClient.Button(buttonText(notification.type()), url));
        }
        return new Outgoing(html + "\n\n" + escape(url), null);
    }

    public String absolute(String path) {
        return properties.appUrl().replaceAll("/+$", "") + path;
    }

    public static String escape(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String icon(String type) {
        return switch (type) {
            case "enrolled" -> "🎓";
            case "new_lesson" -> "📚";
            case "certificate_issued" -> "🏅";
            case "payment_failed" -> "⚠️";
            case "order_refunded" -> "💸";
            case "new_enrollment" -> "🧑‍🎓";
            case "course_completed" -> "🎉";
            case "weekly_report" -> "📊";
            default -> "🔔";
        };
    }

    private String buttonText(String type) {
        return switch (type) {
            case "enrolled" -> "Start learning";
            case "new_lesson" -> "Open course";
            case "certificate_issued" -> "View certificate";
            case "new_enrollment" -> "Open course";
            default -> "Open in CourseAuraHub";
        };
    }
}
