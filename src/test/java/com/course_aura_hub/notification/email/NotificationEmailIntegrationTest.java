package com.course_aura_hub.notification.email;

import com.course_aura_hub.common.email.EmailMessage;
import com.course_aura_hub.learner.LearnerIntegrationTestSupport;
import com.course_aura_hub.notification.model.NotificationType;
import com.course_aura_hub.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationEmailIntegrationTest extends LearnerIntegrationTestSupport {

    private static final Pattern UNSUBSCRIBE = Pattern.compile("(http\\S+/api/v1/notifications/unsubscribe\\S+)");

    @Autowired
    private NotificationService notificationService;
    @Autowired
    private NotificationEmailDelivery delivery;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void instantTypesAreEmailedRightAway_withAnUnsubscribeLinkThatWorks() throws Exception {
        TestUser learner = registerUser();
        UUID userId = userId(learner);
        notify(userId, NotificationType.CERTIFICATE_ISSUED, "Certificate earned " + UUID.randomUUID());

        EmailMessage email = awaitEmail(learner.email(), "Certificate earned");
        assertThat(email.textBody()).contains("/certificates/abc");
        assertThat(email.htmlBody()).contains("Unsubscribe");

        Matcher link = UNSUBSCRIBE.matcher(email.textBody());
        assertThat(link.find()).isTrue();
        URI uri = URI.create(link.group(1));
        mockMvc.perform(get(uri.getPath() + "?" + uri.getRawQuery())).andExpect(status().isOk());
        mockMvc.perform(as(learner, get("/api/v1/me/notification-preferences")))
                .andExpect(jsonPath("$.data[?(@.type == 'certificate_issued')].mode").value("off"));

        emailSender.reset();
        notify(userId, NotificationType.CERTIFICATE_ISSUED, "Another certificate");
        Thread.sleep(800);
        assertThat(emailSender.sentTo(learner.email())).isEmpty();
    }

    @Test
    void forgedUnsubscribeLinksAreRejected() throws Exception {
        TestUser learner = registerUser();
        mockMvc.perform(get("/api/v1/notifications/unsubscribe")
                        .param("user", userId(learner).toString()).param("scope", "all").param("token", "forged"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void digestTypesWaitForTheDailyDigest_andAreNeverSentTwice() throws Exception {
        TestUser learner = registerUser();
        UUID userId = userId(learner);
        emailSender.reset();
        String title = "New lesson " + UUID.randomUUID();
        notify(userId, NotificationType.NEW_LESSON, title);
        Thread.sleep(800);
        assertThat(emailSender.sentTo(learner.email())).isEmpty();

        delivery.sendDigests();
        assertThat(emailSender.sentTo(learner.email())).singleElement()
                .satisfies(email -> assertThat(email.textBody()).contains(title));

        delivery.sendDigests();
        assertThat(emailSender.sentTo(learner.email())).hasSize(1);
    }

    @Test
    void learnersChooseTheModePerType() throws Exception {
        TestUser learner = registerUser();
        mockMvc.perform(as(learner, get("/api/v1/me/notification-preferences")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(NotificationType.values().length))
                .andExpect(jsonPath("$.data[?(@.type == 'new_lesson')].defaultMode").value("digest"));

        mockMvc.perform(asJson(learner, put("/api/v1/me/notification-preferences"),
                        Map.of("preferences", List.of(Map.of("type", "new_lesson", "mode", "instant")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.type == 'new_lesson')].mode").value("instant"));
        notify(userId(learner), NotificationType.NEW_LESSON, "Instant lesson");
        awaitEmail(learner.email(), "Instant lesson");

        mockMvc.perform(asJson(learner, put("/api/v1/me/notification-preferences"),
                        Map.of("preferences", List.of(Map.of("type", "new_lesson", "mode", "sometimes")))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/me/notification-preferences")).andExpect(status().isUnauthorized());
    }

    private void notify(UUID userId, NotificationType type, String title) {
        transactionTemplate.executeWithoutResult(status ->
                notificationService.notifyUsers(List.of(userId), type, title, "Details for " + title, "/certificates/abc"));
    }

    private EmailMessage awaitEmail(String to, String subjectStart) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            Optional<EmailMessage> match = emailSender.sentTo(to).stream()
                    .filter(message -> message.subject().startsWith(subjectStart)).findFirst();
            if (match.isPresent()) {
                return match.get();
            }
            Thread.sleep(100);
        }
        throw new AssertionError("No email starting with '" + subjectStart + "' sent to " + to);
    }

    private UUID userId(TestUser user) {
        return userRepository.findByEmail(user.email()).orElseThrow().getId();
    }
}
