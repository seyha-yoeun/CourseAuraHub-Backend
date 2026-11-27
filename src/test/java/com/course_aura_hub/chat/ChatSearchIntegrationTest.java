package com.course_aura_hub.chat;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChatSearchIntegrationTest extends ChatTestSupport {

    private static final String SEARCH = CHAT + "/search";

    @Test
    void search_findsTextInMyConversationsOnly_khmerIncluded() throws Exception {
        String slug = courseOwnedBy(registerInstructor());
        TestUser jane = registerUser();
        TestUser bo = registerUser();
        TestUser cy = registerUser();
        rename(bo, "Bo", "Kim");
        enroll(jane, slug);
        enroll(bo, slug);
        enroll(cy, slug);
        int withBo = openDirect(jane, bo);
        int notMine = openDirect(bo, cy);
        int rebase = send(bo, withBo, "Which lesson covers Git rebase?");
        send(jane, withBo, "Lesson 4, the rebase part");
        send(bo, notMine, "rebase is hard");
        // Khmer has no spaces between words: a part of a word must still be found
        int khmer = send(jane, withBo, "សូមអរគុណច្រើនសម្រាប់មេរៀន");

        mockMvc.perform(as(jane, get(SEARCH).param("q", "REBASE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.items[1].message.id").value(rebase))
                .andExpect(jsonPath("$.data.items[1].message.sender.name").value("Bo K."))
                .andExpect(jsonPath("$.data.items[1].conversation.id").value(withBo))
                .andExpect(jsonPath("$.data.items[1].conversation.type").value("direct"))
                .andExpect(jsonPath("$.data.items[1].conversation.title").value("Bo K."))
                .andExpect(jsonPath("$.data.items[?(@.conversation.id == " + notMine + ")]").isEmpty())
                .andExpect(jsonPath("$..email").doesNotExist());
        mockMvc.perform(as(jane, get(SEARCH).param("q", "អរគុណ")))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].message.id").value(khmer));
        // % and _ are matched as themselves
        mockMvc.perform(as(jane, get(SEARCH).param("q", "%%"))).andExpect(jsonPath("$.data.items", hasSize(0)));

        mockMvc.perform(as(jane, get(SEARCH).param("q", "r"))).andExpect(status().isBadRequest());
        mockMvc.perform(as(jane, get(SEARCH).param("q", "rebase").param("has", "video"))).andExpect(status().isBadRequest());
        mockMvc.perform(get(SEARCH).param("q", "rebase")).andExpect(status().isUnauthorized());
    }

    @Test
    void search_leavesOutUnsentAndDeletedForMe_andFilters() throws Exception {
        String slug = courseOwnedBy(registerInstructor());
        TestUser jane = registerUser();
        TestUser bo = registerUser();
        TestUser cy = registerUser();
        enroll(jane, slug);
        enroll(bo, slug);
        enroll(cy, slug);
        int withBo = openDirect(jane, bo);
        int withCy = openDirect(jane, cy);
        int unsent = send(jane, withBo, "exam notes v1");
        int forgotten = send(bo, withBo, "exam notes v2");
        int fromBo = send(bo, withBo, "exam notes v3 https://example.com/notes");
        int fromCy = send(cy, withCy, "exam notes from cy");

        mockMvc.perform(as(jane, delete(CHAT + "/messages/" + unsent))).andExpect(status().isOk());
        mockMvc.perform(as(jane, delete(CHAT + "/messages/" + forgotten + "/for-me"))).andExpect(status().isOk());
        mockMvc.perform(as(jane, get(SEARCH).param("q", "exam notes")))
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.items[0].message.id").value(fromCy))
                .andExpect(jsonPath("$.data.items[1].message.id").value(fromBo));

        mockMvc.perform(as(jane, get(SEARCH).param("q", "exam").param("conversationId", String.valueOf(withCy))))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].message.id").value(fromCy));
        mockMvc.perform(as(jane, get(SEARCH).param("q", "exam").param("senderId", id(bo).toString())))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].message.id").value(fromBo));
        // A link filter needs no text
        mockMvc.perform(as(jane, get(SEARCH).param("has", "link")))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].message.id").value(fromBo));
        Instant tomorrow = Instant.now().plus(1, ChronoUnit.DAYS);
        mockMvc.perform(as(jane, get(SEARCH).param("q", "exam").param("from", tomorrow.toString())))
                .andExpect(jsonPath("$.data.items", hasSize(0)));
        mockMvc.perform(as(jane, get(SEARCH).param("q", "exam").param("to", tomorrow.toString())))
                .andExpect(jsonPath("$.data.items", hasSize(2)));

        // Files only, found by the "file" filter
        UUID upload = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into stored_files (id, uploaded_by, purpose, object_key, content_type, size_bytes, original_name,
                                          status, created_at, updated_at)
                values (?, ?, 'CHAT_ATTACHMENT', ?, 'application/pdf', 1024, 'notes.pdf', 'READY', now(), now())
                """, upload, id(bo), "private/chat/" + upload);
        mockMvc.perform(asJson(bo, post(CHAT + "/conversations/" + withBo + "/messages"),
                        Map.of("clientMessageId", UUID.randomUUID(), "attachmentIds", List.of(upload))))
                .andExpect(status().isCreated());
        mockMvc.perform(as(jane, get(SEARCH).param("has", "file")))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].message.attachments[0].fileName").value("notes.pdf"));
    }

    @Test
    void search_pagesOlderHitsWithTheCursor() throws Exception {
        String slug = courseOwnedBy(registerInstructor());
        TestUser jane = registerUser();
        TestUser bo = registerUser();
        enroll(jane, slug);
        enroll(bo, slug);
        int conversation = openDirect(jane, bo);
        for (int i = 0; i < 5; i++) {
            send(bo, conversation, "reminder " + i);
        }
        String first = mockMvc.perform(as(jane, get(SEARCH).param("q", "reminder").param("limit", "3")))
                .andExpect(jsonPath("$.data.items", hasSize(3)))
                .andExpect(jsonPath("$.data.items[0].message.body").value("reminder 4"))
                .andExpect(jsonPath("$.data.nextCursor").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String cursor = JsonPath.read(first, "$.data.nextCursor");
        mockMvc.perform(as(jane, get(SEARCH).param("q", "reminder").param("limit", "3").param("before", cursor)))
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.items[1].message.body").value("reminder 0"))
                .andExpect(jsonPath("$.data.nextCursor").doesNotExist());
    }
}
