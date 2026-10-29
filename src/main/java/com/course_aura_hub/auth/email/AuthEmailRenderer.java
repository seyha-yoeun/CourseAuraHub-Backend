package com.course_aura_hub.auth.email;

import com.course_aura_hub.common.email.AppMailProperties;
import com.course_aura_hub.common.email.EmailMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.time.Duration;

/**
 * Renders {@link AuthEmail} variants into plain-text and HTML bodies. All user-supplied values
 * are HTML-escaped.
 */
@Component
@RequiredArgsConstructor
public class AuthEmailRenderer {

    private final AppMailProperties mailProperties;

    public EmailMessage render(AuthEmail email) {
        String product = mailProperties.fromName();
        return switch (email) {
            case AuthEmail.VerificationCode e -> build(e,
                    "Your " + product + " verification code",
                    "Use this code to verify your email address:",
                    e.code(),
                    "The code expires in " + describe(e.validFor()) + ". If you didn't create an account, you can ignore this email.");
            case AuthEmail.ExistingAccountRegistration e -> build(e,
                    "Sign-up attempt for your " + product + " account",
                    "Someone tried to create a new account with this email address, but you already have one.",
                    null,
                    "If this was you, sign in instead or use \"Forgot password\". If it wasn't, no action is needed; your account is unchanged.");
            case AuthEmail.PasswordReset e -> build(e,
                    "Reset your " + product + " password",
                    "We received a request to reset your password. Open this link to choose a new one:",
                    e.resetLink(),
                    "The link expires in " + describe(e.validFor()) + " and can be used once. If you didn't request a reset, ignore this email; your password stays the same.");
            case AuthEmail.PasswordChanged e -> build(e,
                    "Your " + product + " password was changed",
                    "The password for your account was just changed and all other sessions were signed out.",
                    null,
                    "If you didn't do this, reset your password immediately and contact support.");
            case AuthEmail.MfaEnabled e -> build(e,
                    "Two-factor authentication enabled on " + product,
                    "Two-factor authentication is now required when you sign in.",
                    null,
                    "If you didn't make this change, reset your password and contact support.");
            case AuthEmail.MfaDisabled e -> build(e,
                    "Two-factor authentication disabled on " + product,
                    "Two-factor authentication was turned off for your account.",
                    null,
                    "If you didn't make this change, reset your password immediately and contact support.");
        };
    }

    private EmailMessage build(AuthEmail email, String subject, String intro, String highlight, String outro) {
        String greeting = "Hi " + (email.firstName() == null || email.firstName().isBlank() ? "there" : email.firstName()) + ",";

        StringBuilder text = new StringBuilder()
                .append(greeting).append("\n\n")
                .append(intro).append("\n\n");
        if (highlight != null) {
            text.append(highlight).append("\n\n");
        }
        text.append(outro).append("\n\n— ").append(mailProperties.fromName()).append('\n');

        StringBuilder html = new StringBuilder()
                .append("<div style=\"font-family:Arial,sans-serif;font-size:15px;color:#1f2933;max-width:520px\">")
                .append("<p>").append(HtmlUtils.htmlEscape(greeting)).append("</p>")
                .append("<p>").append(HtmlUtils.htmlEscape(intro)).append("</p>");
        if (highlight != null) {
            String escaped = HtmlUtils.htmlEscape(highlight);
            if (highlight.startsWith("https://") || highlight.startsWith("http://")) {
                html.append("<p><a href=\"").append(escaped).append("\">").append(escaped).append("</a></p>");
            } else {
                html.append("<p style=\"font-size:28px;font-weight:bold;letter-spacing:6px\">").append(escaped).append("</p>");
            }
        }
        html.append("<p>").append(HtmlUtils.htmlEscape(outro)).append("</p>")
                .append("<p>— ").append(HtmlUtils.htmlEscape(mailProperties.fromName())).append("</p></div>");

        return new EmailMessage(email.to(), subject, text.toString(), html.toString());
    }

    private static String describe(Duration duration) {
        long minutes = duration.toMinutes();
        if (minutes >= 60 && minutes % 60 == 0) {
            long hours = minutes / 60;
            return hours + (hours == 1 ? " hour" : " hours");
        }
        return minutes + (minutes == 1 ? " minute" : " minutes");
    }
}
