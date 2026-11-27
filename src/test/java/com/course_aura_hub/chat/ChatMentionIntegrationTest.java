package com.course_aura_hub.chat;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChatMentionIntegrationTest extends ChatTestSupport {

    private int sendMentioning(TestUser from, int conversation, String body, List<UUID> mentioned) throws Exception {
        String response = mockMvc.perform(asJson(from, post(CHAT + "/conversations/" + conversation + "/messages"),
                        Map.of("clientMessageId", UUID.randomUUID(), "body", body, "mentionUserIds", mentioned)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.data.id");
    }

    @Test
    void mentions_reachMembersOnly_evenInAMutedGroup_andCountAsUnread() throws Exception {
        String slug = courseOwnedBy(registerInstructor());
        TestUser owner = registerUser();
        TestUser bo = registerUser();
        TestUser cy = registerUser();
        for (TestUser user : List.of(owner, bo, cy)) {
            enroll(user, slug);
        }
        int group = createGroup(owner, slug, bo, cy);
        join(bo, group);
        // Cy is invited but hasn't joined, so can't read the group yet
        mockMvc.perform(asJson(bo, patch(CHAT + "/conversations/" + group), Map.of("muted", true))).andExpect(status().isOk());

        int message = sendMentioning(owner, group, "@Bo can you share your notes?", List.of(id(bo), id(cy), id(owner)));
        mockMvc.perform(as(bo, get(CHAT + "/conversations/" + group + "/messages")))
                .andExpect(jsonPath("$.data.items[?(@.id == " + message + ")].mentions[0].id", hasItem(id(bo).toString())))
                .andExpect(jsonPath("$..email").doesNotExist());
        assertThat(countNotifications(bo, "CHAT_MENTION", true)).isEqualTo(1);
        assertThat(countNotifications(cy, "CHAT_MENTION", false)).isZero();
        assertThat(countNotifications(owner, "CHAT_MENTION", false)).isZero();
        mockMvc.perform(as(bo, get("/api/v1/me/notifications")))
                .andExpect(jsonPath("$.data.items[?(@.type == 'chat_mention')].link", hasItem("/chat/" + group + "?message=" + message)));

        mockMvc.perform(as(bo, get(CHAT + "/conversations/" + group))).andExpect(jsonPath("$.data.unreadMentions").value(1));
        mockMvc.perform(asJson(bo, put(CHAT + "/conversations/" + group + "/read"), Map.of("messageId", message)))
                .andExpect(status().isOk());
        mockMvc.perform(as(bo, get(CHAT + "/conversations/" + group))).andExpect(jsonPath("$.data.unreadMentions").value(0));
    }

    @Test
    void editing_replacesTheMentions_andOnlyNewlyMentionedPeopleHearOfIt() throws Exception {
        String slug = courseOwnedBy(registerInstructor());
        TestUser owner = registerUser();
        TestUser bo = registerUser();
        TestUser cy = registerUser();
        for (TestUser user : List.of(owner, bo, cy)) {
            enroll(user, slug);
        }
        int group = createGroup(owner, slug, bo, cy);
        join(bo, group);
        join(cy, group);

        int message = sendMentioning(owner, group, "@Bo please review", List.of(id(bo)));
        assertThat(countNotifications(bo, "CHAT_MENTION", false)).isEqualTo(1);

        mockMvc.perform(asJson(owner, patch(CHAT + "/messages/" + message),
                        Map.of("body", "@Cy please review", "mentionUserIds", List.of(id(cy)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mentions", hasSize(1)))
                .andExpect(jsonPath("$.data.mentions[0].id").value(id(cy).toString()));
        assertThat(countNotifications(cy, "CHAT_MENTION", false)).isEqualTo(1);
        assertThat(countNotifications(bo, "CHAT_MENTION", false)).isEqualTo(1);

        // An edit that leaves the list out keeps the mentions
        mockMvc.perform(asJson(owner, patch(CHAT + "/messages/" + message), Map.of("body", "@Cy please review today")))
                .andExpect(jsonPath("$.data.mentions[0].id").value(id(cy).toString()));
        assertThat(countNotifications(cy, "CHAT_MENTION", false)).isEqualTo(1);
    }

    @Test
    void directChats_haveNoMentions() throws Exception {
        String slug = courseOwnedBy(registerInstructor());
        TestUser jane = registerUser();
        TestUser bo = registerUser();
        enroll(jane, slug);
        enroll(bo, slug);
        int conversation = openDirect(jane, bo);
        int message = sendMentioning(jane, conversation, "@Bo hi", List.of(id(bo)));
        mockMvc.perform(as(bo, get(CHAT + "/conversations/" + conversation + "/messages")))
                .andExpect(jsonPath("$.data.items[?(@.id == " + message + ")].mentions[*]").isEmpty());
        assertThat(countNotifications(bo, "CHAT_MENTION", false)).isZero();
    }
}
