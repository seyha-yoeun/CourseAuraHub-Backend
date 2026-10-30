package com.course_aura_hub.support;

import com.course_aura_hub.common.email.EmailDeliveryException;
import com.course_aura_hub.common.email.EmailMessage;
import com.course_aura_hub.common.email.EmailSender;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * In-memory email sender for tests: records messages so tests can read codes and links.
 */
public class RecordingEmailSender implements EmailSender {

    private static final Pattern CODE = Pattern.compile("^(\\d{6,10})$", Pattern.MULTILINE);
    private static final Pattern RESET_TOKEN = Pattern.compile("[?&]token=([A-Za-z0-9_-]+)");

    private final List<EmailMessage> sent = new CopyOnWriteArrayList<>();
    private volatile boolean failing;

    @Override
    public void send(EmailMessage message) {
        if (failing) {
            throw new EmailDeliveryException("Simulated SMTP outage");
        }
        sent.add(message);
    }

    public void reset() {
        sent.clear();
        failing = false;
    }

    public void setFailing(boolean failing) {
        this.failing = failing;
    }

    public List<EmailMessage> sentTo(String email) {
        return sent.stream().filter(m -> m.to().equalsIgnoreCase(email)).toList();
    }

    public Optional<EmailMessage> latestTo(String email) {
        List<EmailMessage> messages = sentTo(email);
        return messages.isEmpty() ? Optional.empty() : Optional.of(messages.get(messages.size() - 1));
    }

    public String latestVerificationCode(String email) {
        EmailMessage message = latestTo(email).orElseThrow(() -> new AssertionError("No email sent to " + email));
        Matcher matcher = CODE.matcher(message.textBody());
        if (!matcher.find()) {
            throw new AssertionError("No verification code in latest email: " + message.subject());
        }
        return matcher.group(1);
    }

    public String latestResetToken(String email) {
        EmailMessage message = latestTo(email).orElseThrow(() -> new AssertionError("No email sent to " + email));
        Matcher matcher = RESET_TOKEN.matcher(message.textBody());
        if (!matcher.find()) {
            throw new AssertionError("No reset link in latest email: " + message.subject());
        }
        return matcher.group(1);
    }
}
