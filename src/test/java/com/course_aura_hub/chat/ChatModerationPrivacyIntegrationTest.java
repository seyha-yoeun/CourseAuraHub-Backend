package com.course_aura_hub.chat;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChatModerationPrivacyIntegrationTest extends ChatTestSupport {

    @Test
    void aReportedMessage_isTheOnlyWayAnAdminSeesIntoADirectConversation() throws Exception {
        String slug = courseOwnedBy(registerInstructor());
        TestUser jane = registerUser();
        TestUser bo = registerUser();
        TestUser admin = registerAdmin();
        enroll(jane, slug);
        enroll(bo, slug);
        int conversation = openDirect(jane, bo);
        send(jane, conversation, "hi");
        int abuse = send(bo, conversation, "something awful");
        send(jane, conversation, "please stop");

        mockMvc.perform(as(admin, get(CHAT + "/conversations/" + conversation + "/messages"))).andExpect(status().isNotFound());
        mockMvc.perform(asJson(registerUser(), post("/api/v1/me/reports"),
                Map.of("targetType", "chat_message", "targetId", abuse, "reason", "Harassment"))).andExpect(status().isNotFound());
        mockMvc.perform(asJson(bo, post("/api/v1/me/reports"),
                Map.of("targetType", "chat_message", "targetId", abuse, "reason", "Mine"))).andExpect(status().isBadRequest());
        mockMvc.perform(asJson(jane, post("/api/v1/me/reports"),
                Map.of("targetType", "chat_message", "targetId", abuse, "reason", "Harassment"))).andExpect(status().isAccepted());

        String reports = mockMvc.perform(as(admin, get("/api/v1/admin/reports").param("size", "100")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(reports, "$.data.items[?(@.targetType == 'chat_message' && @.targetId == " + abuse + ")].id");
        assertThat(ids).hasSize(1);
        int reportId = ids.getFirst();

        long auditsBefore = audits(reportId);
        mockMvc.perform(as(admin, get("/api/v1/admin/chat/reports/" + reportId + "/context")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conversationType").value("direct"))
                .andExpect(jsonPath("$.data.messageId").value(abuse))
                .andExpect(jsonPath("$.data.messages", hasSize(3)))
                .andExpect(jsonPath("$.data.messages[1].body").value("something awful"));
        assertThat(audits(reportId)).isEqualTo(auditsBefore + 1);
        mockMvc.perform(as(jane, get("/api/v1/admin/chat/reports/" + reportId + "/context"))).andExpect(status().isForbidden());

        mockMvc.perform(as(admin, post("/api/v1/admin/reports/" + reportId + "/hide"))).andExpect(status().isOk());
        mockMvc.perform(as(jane, get(CHAT + "/conversations/" + conversation + "/messages")))
                .andExpect(jsonPath("$.data.items[1].hidden").value(true))
                .andExpect(jsonPath("$.data.items[1].body").doesNotExist());
    }

    @Test
    void aRestrictedUser_canReadButNotWrite_untilItIsLifted() throws Exception {
        String slug = courseOwnedBy(registerInstructor());
        TestUser jane = registerUser();
        TestUser bo = registerUser();
        TestUser admin = registerAdmin();
        enroll(jane, slug);
        enroll(bo, slug);
        int conversation = openDirect(jane, bo);

        mockMvc.perform(asJson(admin, post("/api/v1/admin/chat/restrictions/" + id(bo)),
                        Map.of("until", Instant.now().plus(2, ChronoUnit.DAYS).toString(), "reason", "Spam")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reason").value("Spam"));
        mockMvc.perform(asJson(bo, post(CHAT + "/conversations/" + conversation + "/messages"),
                        Map.of("clientMessageId", UUID.randomUUID(), "body", "spam")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Your chat access is restricted until")));
        mockMvc.perform(as(bo, get(CHAT + "/conversations/" + conversation + "/messages"))).andExpect(status().isOk());
        mockMvc.perform(asJson(bo, post(CHAT + "/courses/" + slug + "/groups"), Map.of("title", "Spam club")))
                .andExpect(status().isForbidden());
        mockMvc.perform(asJson(bo, post("/api/v1/admin/chat/restrictions/" + id(jane)),
                Map.of("until", Instant.now().plus(1, ChronoUnit.DAYS).toString(), "reason", "x"))).andExpect(status().isForbidden());

        mockMvc.perform(as(admin, delete("/api/v1/admin/chat/restrictions/" + id(bo)))).andExpect(status().isOk());
        send(bo, conversation, "sorry");
    }

    @Test
    void exportHoldsMyChat_andDeletionAnonymizesItAndHandsMyGroupsOn() throws Exception {
        String slug = courseOwnedBy(registerInstructor());
        TestUser jane = registerUser();
        TestUser bo = registerUser();
        enroll(jane, slug);
        enroll(bo, slug);
        int conversation = openDirect(jane, bo);
        send(jane, conversation, "my words");
        int theirs = send(bo, conversation, "your words");
        mockMvc.perform(asJson(jane, put(CHAT + "/messages/" + theirs + "/reaction"), Map.of("emoji", "👍")))
                .andExpect(status().isOk());
        int group = createGroup(jane, slug, bo);
        join(bo, group);
        mockMvc.perform(as(jane, put(CHAT + "/blocks/" + id(bo)))).andExpect(status().isOk());
        UUID janeId = id(jane);

        mockMvc.perform(as(jane, get("/api/v1/me/data-export")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.chatMessages[0].body").value("my words"))
                .andExpect(jsonPath("$.data.records.chatMemberships", hasSize(2)))
                .andExpect(jsonPath("$.data.records.chatBlocks[0].blockedUserId").value(id(bo).toString()))
                .andExpect(jsonPath("$.data.records.chatReactions[0].emoji").value("👍"));

        mockMvc.perform(asJson(jane, post("/api/v1/me/account/deletion"), Map.of("password", PASSWORD, "confirmation", jane.email())))
                .andExpect(status().isOk());

        mockMvc.perform(as(bo, get(CHAT + "/conversations/" + conversation + "/messages")))
                .andExpect(jsonPath("$.data.items[0].sender.name").value("Deleted user"))
                .andExpect(jsonPath("$.data.items[0].body").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].deleted").value(true));
        mockMvc.perform(as(bo, get(CHAT + "/conversations/" + conversation)))
                .andExpect(jsonPath("$.data.title").value("Deleted user"))
                .andExpect(jsonPath("$.data.canSend").value(false));
        mockMvc.perform(as(bo, get(CHAT + "/conversations/" + group)))
                .andExpect(jsonPath("$.data.myRole").value("owner"))
                .andExpect(jsonPath("$.data.group.memberCount").value(1));
        assertThat(jdbcTemplate.queryForObject("select count(*) from chat_blocks where blocked_id = ?", Integer.class, id(bo)))
                .isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from chat_messages where body = 'my words'", Integer.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from chat_message_reactions where user_id = ?", Integer.class, janeId))
                .isZero();
    }

    private long audits(int reportId) {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from admin_audit_events where action = 'ChatAdminController.reportContext' and resource_id = ?",
                Long.class, String.valueOf(reportId));
        return count == null ? 0 : count;
    }
}
