package com.course_aura_hub.chat;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChatMessagingIntegrationTest extends ChatTestSupport {

    @Test
    void learnersSharingACourse_messageEachOther_byShortName_withReadState() throws Exception {
        String slug = courseOwnedBy(registerInstructor());
        TestUser jane = registerUser();
        TestUser bo = registerUser();
        rename(bo, "Bo", "Kim");
        enroll(jane, slug);
        enroll(bo, slug);

        int conversation = openDirect(jane, bo);
        mockMvc.perform(as(jane, get(CHAT + "/conversations/" + conversation)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("direct"))
                .andExpect(jsonPath("$.data.title").value("Bo K."))
                .andExpect(jsonPath("$.data.peer.name").value("Bo K."))
                .andExpect(jsonPath("$.data.canSend").value(true))
                .andExpect(jsonPath("$..email").doesNotExist());
        assertThat(openDirect(bo, jane)).as("the same pair has one conversation").isEqualTo(conversation);

        UUID clientId = UUID.randomUUID();
        String first = mockMvc.perform(asJson(jane, post(CHAT + "/conversations/" + conversation + "/messages"),
                        Map.of("clientMessageId", clientId, "body", "  Did you finish lesson 3?  ")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.body").value("Did you finish lesson 3?"))
                .andExpect(jsonPath("$.data.mine").value(true))
                // You see your own full name; others see "Jane L."
                .andExpect(jsonPath("$.data.sender.name").value("Jane Learner"))
                .andReturn().getResponse().getContentAsString();
        int messageId = JsonPath.read(first, "$.data.id");
        // A retried send returns the first message instead of storing a copy
        mockMvc.perform(asJson(jane, post(CHAT + "/conversations/" + conversation + "/messages"),
                        Map.of("clientMessageId", clientId, "body", "Did you finish lesson 3?")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(messageId));

        mockMvc.perform(as(bo, get(CHAT + "/conversations")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(conversation))
                .andExpect(jsonPath("$.data.items[0].unread").value(1))
                .andExpect(jsonPath("$.data.items[0].lastMessage.preview").value("Did you finish lesson 3?"));
        mockMvc.perform(as(bo, get(CHAT + "/unread-count"))).andExpect(jsonPath("$.data.total").value(1));
        mockMvc.perform(as(bo, get(CHAT + "/conversations/" + conversation + "/messages")))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].mine").value(false))
                .andExpect(jsonPath("$.data.items[0].sender.name").value("Jane L."))
                .andExpect(jsonPath("$.data.items[0].clientMessageId").doesNotExist());
        assertThat(countNotifications(bo, "CHAT_MESSAGE", true)).isEqualTo(1);

        mockMvc.perform(asJson(bo, put(CHAT + "/conversations/" + conversation + "/read"), Map.of("messageId", messageId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(messageId));
        // Cursors never move back
        mockMvc.perform(asJson(bo, put(CHAT + "/conversations/" + conversation + "/read"), Map.of("messageId", 1)))
                .andExpect(jsonPath("$.data").value(messageId));
        mockMvc.perform(as(bo, get(CHAT + "/unread-count"))).andExpect(jsonPath("$.data.total").value(0));
        assertThat(countNotifications(bo, "CHAT_MESSAGE", true)).as("reading clears the chat notification").isZero();
        mockMvc.perform(as(jane, get(CHAT + "/conversations/" + conversation)))
                .andExpect(jsonPath("$.data.peerReceipt.readMessageId").value(messageId));
    }

    @Test
    void offlineNotifications_areCoalescedPerConversation() throws Exception {
        String slug = courseOwnedBy(registerInstructor());
        TestUser jane = registerUser();
        TestUser bo = registerUser();
        enroll(jane, slug);
        enroll(bo, slug);
        int conversation = openDirect(jane, bo);
        send(jane, conversation, "one");
        send(jane, conversation, "two");
        send(jane, conversation, "three");
        assertThat(countNotifications(bo, "CHAT_MESSAGE", false)).isEqualTo(1);
        mockMvc.perform(as(bo, get("/api/v1/me/notifications")))
                .andExpect(jsonPath("$.data.items[0].type").value("chat_message"))
                .andExpect(jsonPath("$.data.items[0].link").value("/chat/" + conversation));
    }

    @Test
    void withoutASharedCourse_noDirectConversationStarts() throws Exception {
        String slug = courseOwnedBy(registerInstructor());
        TestUser jane = registerUser();
        TestUser stranger = registerUser();
        enroll(jane, slug);
        mockMvc.perform(asJson(jane, post(CHAT + "/conversations/direct"), Map.of("userId", id(stranger))))
                .andExpect(status().isForbidden());
        mockMvc.perform(asJson(jane, post(CHAT + "/conversations/direct"), Map.of("userId", id(jane))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(asJson(jane, post(CHAT + "/conversations/direct"), Map.of("userId", UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    @Test
    void contactSettings_letStaffThrough_andBlocksStopEveryone() throws Exception {
        TestUser instructor = registerInstructor();
        String slug = courseOwnedBy(instructor);
        TestUser jane = registerUser();
        TestUser bo = registerUser();
        TestUser cy = registerUser();
        enroll(jane, slug);
        enroll(bo, slug);
        enroll(cy, slug);

        mockMvc.perform(asJson(bo, put(CHAT + "/settings"), Map.of("directMessages", "nobody")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.directMessages").value("nobody"));
        mockMvc.perform(asJson(jane, post(CHAT + "/conversations/direct"), Map.of("userId", id(bo))))
                .andExpect(status().isForbidden());
        openDirect(instructor, bo);

        int existing = openDirect(cy, jane);
        mockMvc.perform(as(jane, put(CHAT + "/blocks/" + id(cy)))).andExpect(status().isOk());
        mockMvc.perform(as(jane, get(CHAT + "/blocks")))
                .andExpect(jsonPath("$.data[0].user.id").value(id(cy).toString()));
        mockMvc.perform(as(cy, get(CHAT + "/conversations/" + existing)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canSend").value(false));
        mockMvc.perform(asJson(cy, post(CHAT + "/conversations/" + existing + "/messages"),
                        Map.of("clientMessageId", UUID.randomUUID(), "body", "hello?")))
                .andExpect(status().isForbidden());
        // Someone who blocked you is left out of the member directory
        mockMvc.perform(as(cy, get(CHAT + "/courses/" + slug + "/members")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.id == '" + id(jane) + "')]").isEmpty())
                .andExpect(jsonPath("$..email").doesNotExist());

        mockMvc.perform(as(jane, delete(CHAT + "/blocks/" + id(cy)))).andExpect(status().isOk());
        send(cy, existing, "thanks for unblocking");
    }

    @Test
    void privateConversations_are404ForStaffAndAdminsByRole() throws Exception {
        TestUser instructor = registerInstructor();
        String slug = courseOwnedBy(instructor);
        TestUser jane = registerUser();
        TestUser bo = registerUser();
        enroll(jane, slug);
        enroll(bo, slug);
        int conversation = openDirect(jane, bo);
        int message = send(jane, conversation, "just between us");

        for (TestUser outsider : new TestUser[]{instructor, registerAdmin(), registerUser()}) {
            mockMvc.perform(as(outsider, get(CHAT + "/conversations/" + conversation))).andExpect(status().isNotFound());
            mockMvc.perform(as(outsider, get(CHAT + "/conversations/" + conversation + "/messages"))).andExpect(status().isNotFound());
            mockMvc.perform(asJson(outsider, post(CHAT + "/conversations/" + conversation + "/messages"),
                    Map.of("clientMessageId", UUID.randomUUID(), "body", "hi"))).andExpect(status().isNotFound());
            mockMvc.perform(as(outsider, delete(CHAT + "/messages/" + message))).andExpect(status().isNotFound());
        }
        mockMvc.perform(get(CHAT + "/conversations")).andExpect(status().isUnauthorized());
    }

    @Test
    void losingTheSharedCourse_makesADirectConversationReadOnly() throws Exception {
        String slug = courseOwnedBy(registerInstructor());
        TestUser jane = registerUser();
        TestUser bo = registerUser();
        enroll(jane, slug);
        enroll(bo, slug);
        int conversation = openDirect(jane, bo);
        send(jane, conversation, "before the refund");

        unenroll(bo, slug);
        mockMvc.perform(as(bo, get(CHAT + "/conversations/" + conversation + "/messages")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)));
        mockMvc.perform(as(bo, get(CHAT + "/conversations/" + conversation)))
                .andExpect(jsonPath("$.data.canSend").value(false))
                .andExpect(jsonPath("$.data.readOnlyReason").isNotEmpty());
        mockMvc.perform(asJson(jane, post(CHAT + "/conversations/" + conversation + "/messages"),
                Map.of("clientMessageId", UUID.randomUUID(), "body", "still there?"))).andExpect(status().isForbidden());
    }

    @Test
    void courseChannel_isForMembers_staffHideMessages_andNobodyIsNotified() throws Exception {
        TestUser instructor = registerInstructor();
        String slug = courseOwnedBy(instructor);
        TestUser jane = registerUser();
        TestUser bo = registerUser();
        enroll(jane, slug);
        enroll(bo, slug);

        String body = mockMvc.perform(as(jane, get(CHAT + "/courses/" + slug + "/channel")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("course"))
                .andExpect(jsonPath("$.data.course.slug").value(slug))
                .andReturn().getResponse().getContentAsString();
        int channel = JsonPath.read(body, "$.data.id");
        mockMvc.perform(as(registerUser(), get(CHAT + "/courses/" + slug + "/channel"))).andExpect(status().isNotFound());
        mockMvc.perform(as(registerAdmin(), get(CHAT + "/conversations/" + channel))).andExpect(status().isNotFound());

        int spam = send(jane, channel, "buy my notes");
        send(instructor, channel, "Welcome everyone");
        mockMvc.perform(as(bo, get(CHAT + "/conversations/" + channel + "/messages")))
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.items[1].sender.staff").value(true));
        assertThat(countNotifications(bo, "CHAT_MESSAGE", false)).isZero();

        mockMvc.perform(as(bo, post(CHAT + "/messages/" + spam + "/hide"))).andExpect(status().isForbidden());
        mockMvc.perform(as(instructor, post(CHAT + "/messages/" + spam + "/hide")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hidden").value(true))
                .andExpect(jsonPath("$.data.body").doesNotExist());
        mockMvc.perform(as(bo, get(CHAT + "/conversations/" + channel + "/messages")))
                .andExpect(jsonPath("$.data.items[0].hidden").value(true))
                .andExpect(jsonPath("$.data.items[0].body").doesNotExist());

        unenroll(bo, slug);
        mockMvc.perform(as(bo, get(CHAT + "/conversations/" + channel))).andExpect(status().isNotFound());
    }

    @Test
    void support_learnerAndAdmins_closeMakesItReadOnly() throws Exception {
        TestUser jane = registerUser();
        TestUser admin = registerAdmin();
        String body = mockMvc.perform(as(jane, post(CHAT + "/conversations/support")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("support"))
                .andReturn().getResponse().getContentAsString();
        int support = JsonPath.read(body, "$.data.id");
        mockMvc.perform(as(jane, post(CHAT + "/conversations/support"))).andExpect(jsonPath("$.data.id").value(support));
        send(jane, support, "I can't open lesson 2");
        assertThat(countNotifications(admin, "CHAT_MESSAGE", true)).isEqualTo(1);

        mockMvc.perform(as(admin, get("/api/v1/admin/chat/support")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.id == " + support + ")].requester.email").value(jane.email()));
        mockMvc.perform(as(jane, get("/api/v1/admin/chat/support"))).andExpect(status().isForbidden());
        mockMvc.perform(as(registerUser(), get(CHAT + "/conversations/" + support))).andExpect(status().isNotFound());

        send(admin, support, "Fixed, try again");
        mockMvc.perform(as(jane, get("/api/v1/me/notifications")))
                .andExpect(jsonPath("$.data.items[0].title").value("Support replied"));
        mockMvc.perform(as(admin, post(CHAT + "/conversations/support"))).andExpect(status().isForbidden());

        mockMvc.perform(as(admin, post("/api/v1/admin/chat/support/" + support + "/close")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("closed"));
        mockMvc.perform(asJson(jane, post(CHAT + "/conversations/" + support + "/messages"),
                Map.of("clientMessageId", UUID.randomUUID(), "body", "one more"))).andExpect(status().isForbidden());
        mockMvc.perform(as(jane, post(CHAT + "/conversations/support")))
                .andExpect(jsonPath("$.data.id").value(not(support)));
    }

    @Test
    void ownMessages_canBeDeletedForAWhile() throws Exception {
        String slug = courseOwnedBy(registerInstructor());
        TestUser jane = registerUser();
        TestUser bo = registerUser();
        enroll(jane, slug);
        enroll(bo, slug);
        int conversation = openDirect(jane, bo);
        int mistake = send(jane, conversation, "wrong chat");
        int old = send(jane, conversation, "sent long ago");
        jdbcTemplate.update("update chat_messages set created_at = now() - interval '1 hour' where id = ?", old);

        mockMvc.perform(as(bo, delete(CHAT + "/messages/" + mistake))).andExpect(status().isForbidden());
        mockMvc.perform(as(jane, delete(CHAT + "/messages/" + old))).andExpect(status().isBadRequest());
        mockMvc.perform(as(jane, delete(CHAT + "/messages/" + mistake)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deleted").value(true));
        mockMvc.perform(as(bo, get(CHAT + "/conversations/" + conversation + "/messages")))
                .andExpect(jsonPath("$.data.items[0].deleted").value(true))
                .andExpect(jsonPath("$.data.items[0].body").doesNotExist());
    }

    @Test
    void history_pagesBackwards_andConversationsCanBeHiddenAndMuted() throws Exception {
        String slug = courseOwnedBy(registerInstructor());
        TestUser jane = registerUser();
        TestUser bo = registerUser();
        enroll(jane, slug);
        enroll(bo, slug);
        int conversation = openDirect(jane, bo);
        int[] ids = new int[5];
        for (int i = 0; i < 5; i++) {
            ids[i] = send(jane, conversation, "message " + i);
        }
        mockMvc.perform(as(bo, get(CHAT + "/conversations/" + conversation + "/messages").param("limit", "2")))
                .andExpect(jsonPath("$.data.items[0].id").value(ids[3]))
                .andExpect(jsonPath("$.data.items[1].id").value(ids[4]))
                .andExpect(jsonPath("$.data.hasMore").value(true));
        mockMvc.perform(as(bo, get(CHAT + "/conversations/" + conversation + "/messages")
                        .param("before", String.valueOf(ids[3])).param("limit", "2")))
                .andExpect(jsonPath("$.data.items[0].id").value(ids[1]))
                .andExpect(jsonPath("$.data.items[1].id").value(ids[2]));
        mockMvc.perform(as(bo, get(CHAT + "/conversations/" + conversation + "/messages")
                        .param("after", String.valueOf(ids[2]))))
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.hasMore").value(false));

        mockMvc.perform(asJson(bo, patch(CHAT + "/conversations/" + conversation), Map.of("hidden", true, "muted", true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.muted").value(true));
        mockMvc.perform(as(bo, get(CHAT + "/conversations")))
                .andExpect(jsonPath("$.data.items[?(@.id == " + conversation + ")]").isEmpty());
        mockMvc.perform(as(bo, get(CHAT + "/unread-count"))).andExpect(jsonPath("$.data.total").value(0));
        send(jane, conversation, "are you there?");
        mockMvc.perform(as(bo, get(CHAT + "/conversations")))
                .andExpect(jsonPath("$.data.items[0].id").value(conversation));
    }

    @Test
    void replies_quoteAnEarlierMessage_andOnlyFromTheSameConversation() throws Exception {
        String slug = courseOwnedBy(registerInstructor());
        TestUser jane = registerUser();
        TestUser bo = registerUser();
        TestUser cy = registerUser();
        enroll(jane, slug);
        enroll(bo, slug);
        enroll(cy, slug);
        int conversation = openDirect(jane, bo);
        int other = openDirect(jane, cy);
        int question = send(jane, conversation, "Which lesson covers Git rebase?");
        int elsewhere = send(jane, other, "hi cy");

        mockMvc.perform(asJson(bo, post(CHAT + "/conversations/" + conversation + "/messages"),
                        Map.of("clientMessageId", UUID.randomUUID(), "body", "Lesson 4", "replyToMessageId", question)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.replyTo.id").value(question))
                .andExpect(jsonPath("$.data.replyTo.preview").value("Which lesson covers Git rebase?"))
                .andExpect(jsonPath("$.data.replyTo.sender.name").value("Jane L."))
                .andExpect(jsonPath("$.data.replyTo.unavailable").value(false));
        mockMvc.perform(asJson(bo, post(CHAT + "/conversations/" + conversation + "/messages"),
                        Map.of("clientMessageId", UUID.randomUUID(), "body", "sneaky", "replyToMessageId", elsewhere)))
                .andExpect(status().isBadRequest());

        // Unsending the original leaves the reply with an "unavailable" quote, never the old text
        mockMvc.perform(as(jane, delete(CHAT + "/messages/" + question))).andExpect(status().isOk());
        mockMvc.perform(as(bo, get(CHAT + "/conversations/" + conversation + "/messages")))
                .andExpect(jsonPath("$.data.items[1].replyTo.unavailable").value(true))
                .andExpect(jsonPath("$.data.items[1].replyTo.preview").value("Message deleted"));
    }

    @Test
    void deleteForMe_removesAMessageFromMyViewOnly() throws Exception {
        String slug = courseOwnedBy(registerInstructor());
        TestUser jane = registerUser();
        TestUser bo = registerUser();
        enroll(jane, slug);
        enroll(bo, slug);
        int conversation = openDirect(jane, bo);
        send(jane, conversation, "keep this");
        int unwanted = send(jane, conversation, "forget this");
        mockMvc.perform(as(bo, get(CHAT + "/unread-count"))).andExpect(jsonPath("$.data.total").value(2));

        mockMvc.perform(as(bo, delete(CHAT + "/messages/" + unwanted + "/for-me"))).andExpect(status().isOk());
        mockMvc.perform(as(bo, delete(CHAT + "/messages/" + unwanted + "/for-me"))).andExpect(status().isOk());
        mockMvc.perform(as(bo, get(CHAT + "/conversations/" + conversation + "/messages")))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].body").value("keep this"));
        mockMvc.perform(as(bo, get(CHAT + "/unread-count"))).andExpect(jsonPath("$.data.total").value(1));
        mockMvc.perform(as(bo, get(CHAT + "/conversations/" + conversation)))
                .andExpect(jsonPath("$.data.lastMessage.preview").value("keep this"));

        // Jane still has both; an outsider can't touch it
        mockMvc.perform(as(jane, get(CHAT + "/conversations/" + conversation + "/messages")))
                .andExpect(jsonPath("$.data.items", hasSize(2)));
        mockMvc.perform(as(registerUser(), delete(CHAT + "/messages/" + unwanted + "/for-me"))).andExpect(status().isNotFound());
    }

    @Test
    void socketTicket_isIssuedToSignedInUsers() throws Exception {
        mockMvc.perform(as(registerUser(), post(CHAT + "/socket-ticket")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.ticket").isNotEmpty())
                .andExpect(jsonPath("$.data.url").value(startsWith("/ws/chat")));
        mockMvc.perform(post(CHAT + "/socket-ticket").with(csrf())).andExpect(status().isUnauthorized());
    }

    @Test
    void attachments_mustBeMyOwnChatUploads_andAreSentOnce() throws Exception {
        String slug = courseOwnedBy(registerInstructor());
        TestUser jane = registerUser();
        TestUser bo = registerUser();
        enroll(jane, slug);
        enroll(bo, slug);
        int conversation = openDirect(jane, bo);
        UUID mine = upload(jane, "CHAT_ATTACHMENT", "notes.pdf");
        UUID boFile = upload(bo, "CHAT_ATTACHMENT", "secret.pdf");
        UUID avatar = upload(jane, "AVATAR", "me.png");

        mockMvc.perform(asJson(jane, post(CHAT + "/conversations/" + conversation + "/messages"),
                        Map.of("clientMessageId", UUID.randomUUID(), "attachmentIds", java.util.List.of(mine))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.attachments[0].fileName").value("notes.pdf"))
                .andExpect(jsonPath("$.data.attachments[0].contentType").value("application/pdf"));
        mockMvc.perform(as(bo, get(CHAT + "/conversations")))
                .andExpect(jsonPath("$.data.items[0].lastMessage.preview").value("Sent a file"));
        for (UUID refused : new UUID[]{mine, boFile, avatar}) {
            mockMvc.perform(asJson(jane, post(CHAT + "/conversations/" + conversation + "/messages"),
                            Map.of("clientMessageId", UUID.randomUUID(), "attachmentIds", java.util.List.of(refused))))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(asJson(jane, post(CHAT + "/conversations/" + conversation + "/messages"),
                Map.of("clientMessageId", UUID.randomUUID(), "body", "   "))).andExpect(status().isBadRequest());
    }

    @Test
    void reactions_onePerPerson_replacedOrRemoved_andOnlyOnLiveMessages() throws Exception {
        String slug = courseOwnedBy(registerInstructor());
        TestUser jane = registerUser();
        TestUser bo = registerUser();
        TestUser cy = registerUser();
        rename(bo, "Bo", "Kim");
        enroll(jane, slug);
        enroll(bo, slug);
        enroll(cy, slug);
        int conversation = openDirect(jane, bo);
        int message = send(jane, conversation, "Finished lesson 5!");
        String reaction = CHAT + "/messages/" + message + "/reaction";

        mockMvc.perform(asJson(bo, put(reaction), Map.of("emoji", "🎉")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reactions", hasSize(1)))
                .andExpect(jsonPath("$.data.reactions[0].emoji").value("🎉"))
                .andExpect(jsonPath("$.data.reactions[0].count").value(1))
                .andExpect(jsonPath("$.data.reactions[0].mine").value(true));
        mockMvc.perform(asJson(jane, put(reaction), Map.of("emoji", "🎉"))).andExpect(status().isOk());
        // Each viewer sees the count, whether they're in it, and who reacted (newest first, by short name)
        mockMvc.perform(as(jane, get(CHAT + "/conversations/" + conversation + "/messages")))
                .andExpect(jsonPath("$.data.items[0].reactions[0].count").value(2))
                .andExpect(jsonPath("$.data.items[0].reactions[0].mine").value(true))
                .andExpect(jsonPath("$.data.items[0].reactions[0].people", hasSize(2)))
                .andExpect(jsonPath("$.data.items[0].reactions[0].people[1].name").value("Bo K."))
                .andExpect(jsonPath("$..email").doesNotExist());

        // Another emoji replaces mine; the reactions stay in the order they were first used
        mockMvc.perform(asJson(bo, put(reaction), Map.of("emoji", "❤️")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reactions", hasSize(2)))
                .andExpect(jsonPath("$.data.reactions[0].emoji").value("🎉"))
                .andExpect(jsonPath("$.data.reactions[0].count").value(1))
                .andExpect(jsonPath("$.data.reactions[0].mine").value(false))
                .andExpect(jsonPath("$.data.reactions[1].emoji").value("❤️"))
                .andExpect(jsonPath("$.data.reactions[1].mine").value(true));
        mockMvc.perform(as(bo, delete(reaction)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reactions", hasSize(1)));
        mockMvc.perform(as(bo, delete(reaction))).andExpect(status().isOk());

        // Only the offered emoji, and only from the conversation's members
        mockMvc.perform(asJson(bo, put(reaction), Map.of("emoji", "🍕"))).andExpect(status().isBadRequest());
        mockMvc.perform(asJson(bo, put(reaction), Map.of("emoji", ""))).andExpect(status().isBadRequest());
        mockMvc.perform(asJson(cy, put(reaction), Map.of("emoji", "👍"))).andExpect(status().isNotFound());
        mockMvc.perform(as(cy, delete(reaction))).andExpect(status().isNotFound());

        // An unsent message shows no reactions and takes no new ones
        mockMvc.perform(as(jane, delete(CHAT + "/messages/" + message))).andExpect(status().isOk());
        mockMvc.perform(as(bo, get(CHAT + "/conversations/" + conversation + "/messages")))
                .andExpect(jsonPath("$.data.items[0].reactions", hasSize(0)));
        mockMvc.perform(asJson(bo, put(reaction), Map.of("emoji", "👍"))).andExpect(status().isBadRequest());
    }

    @Test
    void reactions_needTheMessageInMyView_andStopWhenBlocked() throws Exception {
        String slug = courseOwnedBy(registerInstructor());
        TestUser jane = registerUser();
        TestUser bo = registerUser();
        enroll(jane, slug);
        enroll(bo, slug);
        int conversation = openDirect(jane, bo);
        int removed = send(jane, conversation, "forget this");
        int kept = send(jane, conversation, "keep this");

        mockMvc.perform(as(bo, delete(CHAT + "/messages/" + removed + "/for-me"))).andExpect(status().isOk());
        mockMvc.perform(asJson(bo, put(CHAT + "/messages/" + removed + "/reaction"), Map.of("emoji", "👍")))
                .andExpect(status().isNotFound());

        mockMvc.perform(asJson(bo, put(CHAT + "/messages/" + kept + "/reaction"), Map.of("emoji", "👍"))).andExpect(status().isOk());
        mockMvc.perform(as(jane, put(CHAT + "/blocks/" + id(bo)))).andExpect(status().isOk());
        mockMvc.perform(asJson(bo, put(CHAT + "/messages/" + kept + "/reaction"), Map.of("emoji", "😂")))
                .andExpect(status().isForbidden());
        // Taking a reaction back still works
        mockMvc.perform(as(bo, delete(CHAT + "/messages/" + kept + "/reaction")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reactions", hasSize(0)));
    }

    @Test
    void peerPresence_defaultsToShown_andIsHiddenAcrossABlock() throws Exception {
        String slug = courseOwnedBy(registerInstructor());
        TestUser jane = registerUser();
        TestUser bo = registerUser();
        enroll(jane, slug);
        enroll(bo, slug);
        int conversation = openDirect(jane, bo);

        mockMvc.perform(as(bo, get(CHAT + "/settings")))
                .andExpect(jsonPath("$.data.showPresence").value(true));
        // Never connected: offline, with no last-seen time
        mockMvc.perform(as(jane, get(CHAT + "/conversations/" + conversation)))
                .andExpect(jsonPath("$.data.peerPresence.online").value(false))
                .andExpect(jsonPath("$.data.peerPresence.lastSeenAt").doesNotExist());

        mockMvc.perform(as(bo, put(CHAT + "/blocks/" + id(jane)))).andExpect(status().isOk());
        mockMvc.perform(as(jane, get(CHAT + "/conversations/" + conversation)))
                .andExpect(jsonPath("$.data.peerPresence").doesNotExist());
        mockMvc.perform(as(bo, get(CHAT + "/conversations/" + conversation)))
                .andExpect(jsonPath("$.data.peerPresence").doesNotExist());
    }

    @Test
    void hiddenConversations_areListedApart_andComeBackWhenUnhiddenOrWrittenIn() throws Exception {
        String slug = courseOwnedBy(registerInstructor());
        TestUser jane = registerUser();
        TestUser bo = registerUser();
        TestUser cy = registerUser();
        enroll(jane, slug);
        enroll(bo, slug);
        enroll(cy, slug);
        int withBo = openDirect(jane, bo);
        int withCy = openDirect(jane, cy);
        send(bo, withBo, "hi jane");
        send(cy, withCy, "hey");

        mockMvc.perform(asJson(jane, patch(CHAT + "/conversations/" + withBo), Map.of("hidden", true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hidden").value(true));
        mockMvc.perform(as(jane, get(CHAT + "/conversations")))
                .andExpect(jsonPath("$.data.items[?(@.id == " + withBo + ")]").isEmpty())
                .andExpect(jsonPath("$.data.items[0].hidden").value(false));
        mockMvc.perform(as(jane, get(CHAT + "/conversations?hidden=true")))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].id").value(withBo))
                .andExpect(jsonPath("$.data.items[0].hidden").value(true));

        // Unhiding puts it straight back
        mockMvc.perform(asJson(jane, patch(CHAT + "/conversations/" + withBo), Map.of("hidden", false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hidden").value(false));
        mockMvc.perform(as(jane, get(CHAT + "/conversations?hidden=true")))
                .andExpect(jsonPath("$.data.items", hasSize(0)));

        // So does a new message
        mockMvc.perform(asJson(jane, patch(CHAT + "/conversations/" + withCy), Map.of("hidden", true))).andExpect(status().isOk());
        send(cy, withCy, "still there?");
        mockMvc.perform(as(jane, get(CHAT + "/conversations/" + withCy)))
                .andExpect(jsonPath("$.data.hidden").value(false));
        mockMvc.perform(as(jane, get(CHAT + "/conversations?hidden=true")))
                .andExpect(jsonPath("$.data.items", hasSize(0)));
    }

    /** A finished upload, as FileService leaves it after completeUpload. */
    private UUID upload(TestUser owner, String purpose, String name) {
        UUID fileId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into stored_files (id, uploaded_by, purpose, object_key, content_type, size_bytes, original_name,
                                          status, created_at, updated_at)
                values (?, ?, ?, ?, 'application/pdf', 1024, ?, 'READY', now(), now())
                """, fileId, id(owner), purpose, "private/chat/" + fileId, name);
        return fileId;
    }
}
