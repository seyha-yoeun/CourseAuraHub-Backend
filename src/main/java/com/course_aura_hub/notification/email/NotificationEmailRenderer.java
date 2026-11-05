package com.course_aura_hub.notification.email;

import com.course_aura_hub.common.email.AppMailProperties;
import com.course_aura_hub.common.email.EmailMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.util.List;

/** Plain-text and HTML bodies for notification emails. Every dynamic value is HTML-escaped. */
@Component
@RequiredArgsConstructor
public class NotificationEmailRenderer {

    /** One notification as the email shows it; {@code link} is a frontend path or null. */
    public record Item(String title, String message, String link) {
    }

    private final AppMailProperties mailProperties;
    private final NotificationEmailProperties properties;

    public EmailMessage single(String to, String firstName, Item item, String unsubscribeUrl) {
        return build(to, firstName, item.title(), List.of(item), null, unsubscribeUrl);
    }

    public EmailMessage digest(String to, String firstName, List<Item> items, int remaining, String unsubscribeUrl) {
        String subject = items.size() == 1
                ? "1 update from " + mailProperties.fromName()
                : items.size() + remaining + " updates from " + mailProperties.fromName();
        String more = remaining > 0 ? "and " + remaining + " more in the app." : null;
        return build(to, firstName, subject, items, more, unsubscribeUrl);
    }

    private EmailMessage build(String to, String firstName, String subject, List<Item> items, String footer, String unsubscribeUrl) {
        String greeting = "Hi " + (firstName == null || firstName.isBlank() ? "there" : firstName) + ",";
        StringBuilder text = new StringBuilder(greeting).append("\n\n");
        StringBuilder html = new StringBuilder("<p>").append(HtmlUtils.htmlEscape(greeting)).append("</p>");
        for (Item item : items) {
            String url = absolute(item.link());
            text.append("* ").append(item.title()).append('\n').append(item.message()).append('\n');
            html.append("<h3 style=\"margin:16px 0 4px\">").append(HtmlUtils.htmlEscape(item.title())).append("</h3>")
                    .append("<p style=\"margin:0 0 4px\">").append(HtmlUtils.htmlEscape(item.message())).append("</p>");
            if (url != null) {
                text.append(url).append('\n');
                html.append("<p style=\"margin:0\"><a href=\"").append(HtmlUtils.htmlEscape(url)).append("\">Open in ")
                        .append(HtmlUtils.htmlEscape(mailProperties.fromName())).append("</a></p>");
            }
            text.append('\n');
        }
        if (footer != null) {
            text.append(footer).append("\n\n");
            html.append("<p>").append(HtmlUtils.htmlEscape(footer)).append("</p>");
        }
        text.append("Manage which emails you get: ").append(properties.appUrl().replaceAll("/+$", ""))
                .append("/account/notifications\nUnsubscribe from these emails: ").append(unsubscribeUrl).append('\n');
        html.append("<hr><p style=\"font-size:12px;color:#666\"><a href=\"")
                .append(HtmlUtils.htmlEscape(properties.appUrl().replaceAll("/+$", "") + "/account/notifications"))
                .append("\">Email settings</a> · <a href=\"").append(HtmlUtils.htmlEscape(unsubscribeUrl))
                .append("\">Unsubscribe</a></p>");
        return new EmailMessage(to, subject, text.toString(), html.toString());
    }

    /** Only app-relative paths become links, so a stored value can never point mail readers elsewhere. */
    private String absolute(String link) {
        if (link == null || !link.startsWith("/") || link.startsWith("//")) {
            return null;
        }
        return properties.appUrl().replaceAll("/+$", "") + link;
    }
}
