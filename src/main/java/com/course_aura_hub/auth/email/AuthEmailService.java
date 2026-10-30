package com.course_aura_hub.auth.email;

import com.course_aura_hub.auth.model.AuditEventType;
import com.course_aura_hub.auth.model.AuditOutcome;
import com.course_aura_hub.auth.service.AuthAuditEntry;
import com.course_aura_hub.auth.service.AuthAuditService;
import com.course_aura_hub.common.email.EmailDeliveryException;
import com.course_aura_hub.common.email.EmailSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Sends auth emails. Must be called outside database transactions so a slow SMTP server never
 * holds row locks or connections.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthEmailService {

    private final EmailSender emailSender;
    private final AuthEmailRenderer renderer;
    private final AuthAuditService auditService;

    /**
     * @throws EmailDeliveryException when the provider fails; callers decide whether that is fatal
     */
    public void send(AuthEmail email) {
        try {
            emailSender.send(renderer.render(email));
        } catch (EmailDeliveryException ex) {
            auditService.record(AuthAuditEntry.builder()
                    .type(AuditEventType.EMAIL_DELIVERY_FAILED)
                    .outcome(AuditOutcome.FAILURE)
                    .email(email.to())
                    .reason(email.getClass().getSimpleName())
                    .metadata(Map.of("template", email.getClass().getSimpleName()))
                    .build());
            throw ex;
        }
    }

    /** For notifications whose failure must not change the API response (and must not reveal anything). */
    public void sendQuietly(AuthEmail email) {
        try {
            send(email);
        } catch (EmailDeliveryException ex) {
            log.warn("Notification email not delivered template={}", email.getClass().getSimpleName());
        }
    }
}
