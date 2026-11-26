package com.course_aura_hub.chat;

import com.course_aura_hub.auth.model.Role;
import com.course_aura_hub.learner.LearnerIntegrationTestSupport;
import com.jayway.jsonpath.JsonPath;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Chat test helpers. Every test makes its own course, so counts and memberships never leak between tests. */
abstract class ChatTestSupport extends LearnerIntegrationTestSupport {

    static final String CHAT = "/api/v1/me/chat";

    protected TestUser registerInstructor() throws Exception {
        TestUser user = registerUser();
        userRepository.findByEmail(user.email()).ifPresent(entity -> {
            entity.setRole(Role.ROLE_INSTRUCTOR);
            userRepository.save(entity);
        });
        return user;
    }

    protected void rename(TestUser user, String first, String last) {
        jdbcTemplate.update("update users set first_name = ?, last_name = ? where email = ?", first, last, user.email());
    }

    /** A published free course owned by the instructor, so platform admins are not its staff. */
    protected String courseOwnedBy(TestUser instructor) throws Exception {
        String body = mockMvc.perform(asJson(registerAdmin(), post("/api/v1/courses"),
                        Map.of("title", "Chat " + System.nanoTime(), "status", "published")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String slug = JsonPath.read(body, "$.data.slug");
        jdbcTemplate.update("update courses set owner_id = ? where slug = ?", id(instructor), slug);
        return slug;
    }

    /** What a refund or an admin removal leaves behind: no enrollment row and no course access. */
    protected void unenroll(TestUser user, String slug) {
        for (String table : List.of("student_courses", "enrollments")) {
            jdbcTemplate.update("delete from " + table + """
                     where course_id = (select id from courses where slug = ?)
                      and student_id = (select s.id from students s join users u on u.id = s.user_id where u.email = ?)
                    """, slug, user.email());
        }
    }

    protected UUID id(TestUser user) {
        return userId(user.email());
    }

    protected int openDirect(TestUser from, TestUser to) throws Exception {
        String body = mockMvc.perform(asJson(from, post(CHAT + "/conversations/direct"), Map.of("userId", id(to))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.id");
    }

    protected int send(TestUser from, int conversationId, String text) throws Exception {
        String body = mockMvc.perform(asJson(from, post(CHAT + "/conversations/" + conversationId + "/messages"),
                        Map.of("clientMessageId", UUID.randomUUID(), "body", text)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.id");
    }

    protected int createGroup(TestUser owner, String slug, TestUser... invitees) throws Exception {
        List<UUID> ids = java.util.Arrays.stream(invitees).map(this::id).toList();
        String body = mockMvc.perform(asJson(owner, post(CHAT + "/courses/" + slug + "/groups"),
                        Map.of("title", "Study group", "inviteUserIds", ids)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.conversation.id");
    }

    protected int inviteId(TestUser invitee, int groupId) throws Exception {
        String body = mockMvc.perform(as(invitee, get(CHAT + "/invites")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(body, "$.data[?(@.conversationId == " + groupId + ")].id");
        if (ids.isEmpty()) {
            throw new AssertionError("No open invite to group " + groupId + " for " + invitee.email());
        }
        return ids.getFirst();
    }

    protected void join(TestUser invitee, int groupId) throws Exception {
        mockMvc.perform(as(invitee, post(CHAT + "/invites/" + inviteId(invitee, groupId) + "/accept")))
                .andExpect(status().isOk());
    }

    protected int countNotifications(TestUser user, String type, boolean unreadOnly) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*) from notifications n join users u on u.id = n.user_id
                where u.email = ? and n.type = ? and (? = false or n.read_at is null)
                """, Integer.class, user.email(), type, unreadOnly);
        return count == null ? 0 : count;
    }
}
