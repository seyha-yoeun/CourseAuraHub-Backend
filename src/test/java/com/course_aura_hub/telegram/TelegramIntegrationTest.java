package com.course_aura_hub.telegram;

import com.course_aura_hub.learner.LearnerIntegrationTestSupport;
import com.course_aura_hub.telegram.client.TelegramClient;
import com.course_aura_hub.telegram.client.TelegramException;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {
        "app.telegram.bot-token=test-token",
        "app.telegram.bot-username=TestBot",
        "app.telegram.webhook-url=https://example.com/api/v1/telegram/webhook",
        "app.telegram.webhook-secret=test-secret"
})
class TelegramIntegrationTest extends LearnerIntegrationTestSupport {

    private static final String TELEGRAM = "/api/v1/me/telegram";
    private static final String WEBHOOK = "/api/v1/telegram/webhook";
    private static final long WAIT_MS = 3000;
    private static final AtomicLong CHAT_IDS = new AtomicLong(1000);

    @MockitoBean
    private TelegramClient client;

    @BeforeEach
    void resetClient() {
        clearInvocations(client);
    }

    private ResultActions send(long chatId, String chatType, String text, String secret) throws Exception {
        var request = post(WEBHOOK)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "update_id", 1,
                        "message", Map.of(
                                "text", text,
                                "chat", Map.of("id", chatId, "type", chatType),
                                "from", Map.of("first_name", "Jane", "username", "jane")))));
        return mockMvc.perform(secret == null ? request : request.header("X-Telegram-Bot-Api-Secret-Token", secret));
    }

    private void say(long chatId, String text) throws Exception {
        send(chatId, "private", text, "test-secret").andExpect(status().isOk());
    }

    private String connectToken(TestUser user) throws Exception {
        String body = mockMvc.perform(as(user, post(TELEGRAM + "/link")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String url = JsonPath.read(body, "$.data.url");
        assertThat(url).startsWith("https://t.me/TestBot?start=");
        return url.substring(url.indexOf('=') + 1);
    }

    private long connect(TestUser user) throws Exception {
        long chatId = CHAT_IDS.incrementAndGet();
        say(chatId, "/start " + connectToken(user));
        clearInvocations(client);
        return chatId;
    }

    private String lastReply(long chatId) {
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(client, timeout(WAIT_MS).atLeastOnce()).sendMessage(eq(chatId), html.capture(), any());
        return html.getValue();
    }

    @Test
    void connecting_linksThePrivateChat() throws Exception {
        TestUser learner = registerUser();
        mockMvc.perform(as(learner, get(TELEGRAM)))
                .andExpect(jsonPath("$.data.available").value(true))
                .andExpect(jsonPath("$.data.connected").value(false));

        long chatId = CHAT_IDS.incrementAndGet();
        say(chatId, "/start " + connectToken(learner));

        assertThat(lastReply(chatId)).contains("Telegram connected", "/progress").doesNotContain("/stats");
        mockMvc.perform(as(learner, get(TELEGRAM)))
                .andExpect(jsonPath("$.data.connected").value(true))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.telegramName").value("@jane"));
    }

    @Test
    void connectToken_worksOnce() throws Exception {
        TestUser learner = registerUser();
        String token = connectToken(learner);
        say(CHAT_IDS.incrementAndGet(), "/start " + token);

        long secondChat = CHAT_IDS.incrementAndGet();
        say(secondChat, "/start " + token);

        assertThat(lastReply(secondChat)).contains("expired or was already used");
    }

    @Test
    void groupChats_cannotConnect() throws Exception {
        TestUser learner = registerUser();

        send(CHAT_IDS.incrementAndGet(), "group", "/start " + connectToken(learner), "test-secret").andExpect(status().isOk());

        mockMvc.perform(as(learner, get(TELEGRAM))).andExpect(jsonPath("$.data.connected").value(false));
        verify(client, never()).sendMessage(anyLong(), anyString(), any());
    }

    @Test
    void webhook_requiresTheSecret() throws Exception {
        send(1, "private", "/help", "wrong").andExpect(status().isUnauthorized());
        send(1, "private", "/help", null).andExpect(status().isUnauthorized());
    }

    @Test
    void notifications_reachOnlyTheirOwner() throws Exception {
        TestUser learner = registerUser();
        TestUser other = registerUser();
        long learnerChat = connect(learner);
        long otherChat = connect(other);

        enroll(learner, FREE_COURSE);

        verify(client, timeout(WAIT_MS)).sendMessage(eq(learnerChat), contains("enrolled"), any());
        verify(client, after(300).never()).sendMessage(eq(otherChat), anyString(), any());
    }

    @Test
    void admins_getEnrollmentAlertsAndLearnersDoNot() throws Exception {
        TestUser admin = registerAdmin();
        TestUser learner = registerUser();
        long adminChat = connect(admin);
        long learnerChat = connect(learner);

        enroll(learner, FREE_COURSE);

        verify(client, timeout(WAIT_MS)).sendMessage(eq(adminChat), contains("New enrollment"), any());
        verify(client, never()).sendMessage(eq(learnerChat), contains("New enrollment"), any());
    }

    @Test
    void pausing_stopsDelivery() throws Exception {
        TestUser learner = registerUser();
        long chatId = connect(learner);

        mockMvc.perform(asJson(learner, patch(TELEGRAM), Map.of("enabled", false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));
        enroll(learner, FREE_COURSE);

        verify(client, after(300).never()).sendMessage(eq(chatId), anyString(), any());
    }

    @Test
    void blockingTheBot_disconnectsTheChat() throws Exception {
        TestUser learner = registerUser();
        long chatId = connect(learner);
        doThrow(new TelegramException("blocked", true)).when(client).sendMessage(eq(chatId), anyString(), any());

        enroll(learner, FREE_COURSE);

        verify(client, timeout(WAIT_MS)).sendMessage(eq(chatId), anyString(), any());
        Thread.sleep(300);
        mockMvc.perform(as(learner, get(TELEGRAM))).andExpect(jsonPath("$.data.connected").value(false));
    }

    @Test
    void commands_matchTheRole() throws Exception {
        TestUser learner = registerUser();
        TestUser admin = registerAdmin();
        enroll(learner, FREE_COURSE);
        long learnerChat = connect(learner);
        long adminChat = connect(admin);

        say(learnerChat, "/progress");
        assertThat(lastReply(learnerChat)).contains("Your learning", "Git &amp; GitHub Fundamentals");

        clearInvocations(client);
        say(learnerChat, "/stats");
        assertThat(lastReply(learnerChat)).doesNotContain("Revenue").contains("/progress");

        say(adminChat, "/stats");
        assertThat(lastReply(adminChat)).contains("Platform overview", "Revenue");
    }

    @Test
    void unconnectedChats_onlySeeHowToConnect() throws Exception {
        long chatId = CHAT_IDS.incrementAndGet();

        say(chatId, "/stats");

        assertThat(lastReply(chatId)).contains("Connect Telegram").doesNotContain("Revenue");
    }

    @Test
    void disconnecting_fromTheAppStopsDelivery() throws Exception {
        TestUser learner = registerUser();
        long chatId = connect(learner);

        mockMvc.perform(as(learner, delete(TELEGRAM))).andExpect(status().isOk());
        enroll(learner, FREE_COURSE);

        verify(client, after(300).never()).sendMessage(eq(chatId), anyString(), any());
    }
}
