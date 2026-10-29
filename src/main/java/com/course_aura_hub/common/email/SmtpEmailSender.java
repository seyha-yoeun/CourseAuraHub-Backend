package com.course_aura_hub.common.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/**
 * Sends mail through the SMTP server configured under 'spring.mail'.
 */
@Slf4j
@RequiredArgsConstructor
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final AppMailProperties properties;

    @Override
    public void send(EmailMessage message) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            boolean multipart = message.htmlBody() != null;
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, multipart, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.from(), properties.fromName());
            helper.setTo(message.to());
            helper.setSubject(message.subject());
            if (multipart) {
                helper.setText(message.textBody(), message.htmlBody());
            } else {
                helper.setText(message.textBody());
            }
            mailSender.send(mimeMessage);
            log.info("Email sent subject=\"{}\"", message.subject());
        } catch (MailException | MessagingException | UnsupportedEncodingException ex) {
            // Provider errors can echo recipient data or server details; keep them out of the exception message
            log.warn("SMTP delivery failed subject=\"{}\" cause={}", message.subject(), ex.getClass().getSimpleName());
            throw new EmailDeliveryException("SMTP delivery failed", ex);
        }
    }
}
