package com.course_aura_hub.chat;

import com.course_aura_hub.chat.service.ChatGroupService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChatGroupIntegrationTest extends ChatTestSupport {

    @Autowired
    private ChatGroupService groupService;

    @Test
    void aGroup_isJoinedOnlyByAcceptingAnInvite_andIsInvisibleToCourseStaff() throws Exception {
        TestUser instructor = registerInstructor();
        String slug = courseOwnedBy(instructor);
        TestUser owner = registerUser();
        TestUser bo = registerUser();
        TestUser outsider = registerUser();
        enroll(owner, slug);
        enroll(bo, slug);

        String body = mockMvc.perform(asJson(owner, post(CHAT + "/courses/" + slug + "/groups"),
                        Map.of("title", "Exam prep", "inviteUserIds", List.of(id(bo), id(outsider)))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.conversation.type").value("group"))
                .andExpect(jsonPath("$.data.conversation.myRole").value("owner"))
                .andExpect(jsonPath("$.data.invites[0].status").value("invited"))
                .andExpect(jsonPath("$.data.invites[1].status").value("unavailable"))
                .andReturn().getResponse().getContentAsString();
        int group = JsonPath.read(body, "$.data.conversation.id");

        // Invited is not joined
        mockMvc.perform(as(bo, get(CHAT + "/conversations/" + group))).andExpect(status().isNotFound());
        mockMvc.perform(as(bo, get(CHAT + "/invites")))
                .andExpect(jsonPath("$.data[0].groupTitle").value("Exam prep"))
                .andExpect(jsonPath("$.data[0].course.slug").value(slug));
        join(bo, group);
        mockMvc.perform(as(bo, get(CHAT + "/groups/" + group + "/members")))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].role").value("owner"))
                .andExpect(jsonPath("$.data[1].role").value("member"))
                .andExpect(jsonPath("$..email").doesNotExist());
        mockMvc.perform(as(bo, get(CHAT + "/conversations/" + group + "/messages")))
                .andExpect(jsonPath("$.data.items[*].kind", hasItem("system")))
                .andExpect(jsonPath("$.data.items[*].body", hasItem("Jane L. joined")));
        mockMvc.perform(as(owner, get(CHAT + "/courses/" + slug + "/groups")))
                .andExpect(jsonPath("$.data[0].id").value(group));

        // Course staff and admins have no say in private groups
        for (TestUser staff : new TestUser[]{instructor, registerAdmin()}) {
            mockMvc.perform(as(staff, get(CHAT + "/conversations/" + group))).andExpect(status().isNotFound());
            mockMvc.perform(as(staff, get(CHAT + "/groups/" + group + "/members"))).andExpect(status().isNotFound());
            mockMvc.perform(as(staff, delete(CHAT + "/groups/" + group + "/members/" + id(bo)))).andExpect(status().isNotFound());
        }
        mockMvc.perform(as(instructor, get(CHAT + "/courses/" + slug + "/groups")))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void roles_decideWhoInvitesRemovesAndEdits() throws Exception {
        String slug = courseOwnedBy(registerInstructor());
        TestUser owner = registerUser();
        TestUser admin = registerUser();
        TestUser member = registerUser();
        TestUser newcomer = registerUser();
        for (TestUser user : List.of(owner, admin, member, newcomer)) {
            enroll(user, slug);
        }
        int group = createGroup(owner, slug, admin, member);
        join(admin, group);
        join(member, group);

        mockMvc.perform(asJson(member, post(CHAT + "/groups/" + group + "/invites"), Map.of("userIds", List.of(id(newcomer)))))
                .andExpect(status().isForbidden());
        mockMvc.perform(asJson(member, put(CHAT + "/groups/" + group + "/members/" + id(admin) + "/role"), Map.of("role", "admin")))
                .andExpect(status().isForbidden());
        mockMvc.perform(asJson(owner, put(CHAT + "/groups/" + group + "/members/" + id(admin) + "/role"), Map.of("role", "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("admin"));

        mockMvc.perform(asJson(admin, post(CHAT + "/groups/" + group + "/invites"), Map.of("userIds", List.of(id(newcomer)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.results[0].status").value("invited"));
        mockMvc.perform(asJson(admin, post(CHAT + "/groups/" + group + "/invites"), Map.of("userIds", List.of(id(newcomer), id(member)))))
                .andExpect(jsonPath("$.data.results[0].status").value("already_invited"))
                .andExpect(jsonPath("$.data.results[1].status").value("already_member"));
        int invite = inviteId(newcomer, group);
        mockMvc.perform(as(member, delete(CHAT + "/groups/" + group + "/invites/" + invite))).andExpect(status().isForbidden());
        mockMvc.perform(as(admin, delete(CHAT + "/groups/" + group + "/invites/" + invite))).andExpect(status().isOk());
        mockMvc.perform(as(newcomer, post(CHAT + "/invites/" + invite + "/accept"))).andExpect(status().isConflict());

        // With member invites on, members may invite too
        String group1 = mockMvc.perform(as(owner, get(CHAT + "/conversations/" + group))).andReturn().getResponse().getContentAsString();
        int version = JsonPath.read(group1, "$.data.group.version");
        mockMvc.perform(asJson(owner, patch(CHAT + "/groups/" + group), Map.of("allowMemberInvites", true, "version", version)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.group.allowMemberInvites").value(true));
        mockMvc.perform(asJson(member, post(CHAT + "/groups/" + group + "/invites"), Map.of("userIds", List.of(id(newcomer)))))
                .andExpect(jsonPath("$.data.results[0].status").value("invited"));

        mockMvc.perform(as(admin, delete(CHAT + "/groups/" + group + "/members/" + id(owner)))).andExpect(status().isForbidden());
        mockMvc.perform(as(member, delete(CHAT + "/groups/" + group + "/members/" + id(admin)))).andExpect(status().isForbidden());
        mockMvc.perform(as(admin, delete(CHAT + "/groups/" + group + "/members/" + id(member)))).andExpect(status().isOk());
        mockMvc.perform(as(member, get(CHAT + "/conversations/" + group))).andExpect(status().isNotFound());
        mockMvc.perform(asJson(member, post(CHAT + "/conversations/" + group + "/messages"),
                Map.of("clientMessageId", UUID.randomUUID(), "body", "let me back"))).andExpect(status().isNotFound());
        mockMvc.perform(asJson(admin, patch(CHAT + "/groups/" + group), Map.of("title", "Renamed", "version", version + 1)))
                .andExpect(status().isOk());
        mockMvc.perform(as(admin, delete(CHAT + "/groups/" + group))).andExpect(status().isForbidden());
    }

    @Test
    void aStaleEdit_isRefused() throws Exception {
        String slug = courseOwnedBy(registerInstructor());
        TestUser owner = registerUser();
        enroll(owner, slug);
        int group = createGroup(owner, slug);
        mockMvc.perform(asJson(owner, patch(CHAT + "/groups/" + group), Map.of("title", "First", "version", 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("First"))
                .andExpect(jsonPath("$.data.group.version").value(1));
        mockMvc.perform(asJson(owner, patch(CHAT + "/groups/" + group), Map.of("title", "Second", "version", 0)))
                .andExpect(status().isConflict());
    }

    @Test
    void anOwnerWhoLeaves_handsOverToTheLongestServingAdmin_andTheLastOneOutArchives() throws Exception {
        String slug = courseOwnedBy(registerInstructor());
        TestUser owner = registerUser();
        TestUser early = registerUser();
        TestUser admin = registerUser();
        for (TestUser user : List.of(owner, early, admin)) {
            enroll(user, slug);
        }
        int group = createGroup(owner, slug, early, admin);
        join(early, group);
        join(admin, group);
        mockMvc.perform(asJson(owner, put(CHAT + "/groups/" + group + "/members/" + id(admin) + "/role"), Map.of("role", "admin")));

        mockMvc.perform(as(owner, post(CHAT + "/groups/" + group + "/leave"))).andExpect(status().isOk());
        mockMvc.perform(as(admin, get(CHAT + "/conversations/" + group)))
                .andExpect(jsonPath("$.data.myRole").value("owner"))
                .andExpect(jsonPath("$.data.group.ownerId").value(id(admin).toString()));
        assertThat(activeOwners(group)).isEqualTo(1);

        mockMvc.perform(as(admin, post(CHAT + "/groups/" + group + "/leave"))).andExpect(status().isOk());
        mockMvc.perform(as(early, get(CHAT + "/conversations/" + group)))
                .andExpect(jsonPath("$.data.myRole").value("owner"));
        mockMvc.perform(as(early, post(CHAT + "/groups/" + group + "/leave"))).andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject("select status from chat_conversations where id = ?", String.class, group))
                .isEqualTo("ARCHIVED");
    }

    @Test
    void transfer_makesTheOldOwnerAnAdmin_andArchiveMakesTheGroupReadOnly() throws Exception {
        String slug = courseOwnedBy(registerInstructor());
        TestUser owner = registerUser();
        TestUser bo = registerUser();
        TestUser invited = registerUser();
        for (TestUser user : List.of(owner, bo, invited)) {
            enroll(user, slug);
        }
        int group = createGroup(owner, slug, bo, invited);
        join(bo, group);

        mockMvc.perform(asJson(bo, post(CHAT + "/groups/" + group + "/transfer"), Map.of("userId", id(owner))))
                .andExpect(status().isForbidden());
        mockMvc.perform(asJson(owner, post(CHAT + "/groups/" + group + "/transfer"), Map.of("userId", id(invited))))
                .andExpect(status().isNotFound());
        mockMvc.perform(asJson(owner, post(CHAT + "/groups/" + group + "/transfer"), Map.of("userId", id(bo))))
                .andExpect(status().isOk());
        mockMvc.perform(as(owner, get(CHAT + "/conversations/" + group))).andExpect(jsonPath("$.data.myRole").value("admin"));
        assertThat(activeOwners(group)).isEqualTo(1);

        mockMvc.perform(as(owner, delete(CHAT + "/groups/" + group))).andExpect(status().isForbidden());
        mockMvc.perform(as(bo, delete(CHAT + "/groups/" + group))).andExpect(status().isOk());
        mockMvc.perform(as(owner, get(CHAT + "/conversations/" + group)))
                .andExpect(jsonPath("$.data.status").value("archived"))
                .andExpect(jsonPath("$.data.canSend").value(false));
        mockMvc.perform(asJson(owner, post(CHAT + "/conversations/" + group + "/messages"),
                Map.of("clientMessageId", UUID.randomUUID(), "body", "hello?"))).andExpect(status().isForbidden());
        mockMvc.perform(as(invited, get(CHAT + "/invites"))).andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void losingCourseAccess_endsGroupAccessAtOnce_andMaintenanceHandsOwnershipOn() throws Exception {
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

        unenroll(cy, slug);
        mockMvc.perform(as(cy, get(CHAT + "/conversations/" + group))).andExpect(status().isNotFound());
        unenroll(owner, slug);
        mockMvc.perform(as(owner, get(CHAT + "/conversations/" + group))).andExpect(status().isNotFound());

        groupService.runMaintenance();
        mockMvc.perform(as(bo, get(CHAT + "/conversations/" + group)))
                .andExpect(jsonPath("$.data.myRole").value("owner"))
                .andExpect(jsonPath("$.data.group.memberCount").value(1));
        assertThat(jdbcTemplate.queryForObject("""
                select left_reason from chat_participants p join users u on u.id = p.user_id
                where p.conversation_id = ? and u.email = ?""", String.class, group, cy.email())).isEqualTo("INELIGIBLE");

        // Coming back to the course does not bring the membership back
        enroll(cy, slug);
        mockMvc.perform(as(cy, get(CHAT + "/conversations/" + group))).andExpect(status().isNotFound());
    }

    @Test
    void expiredInvites_cantBeAccepted_andAreClosedByMaintenance() throws Exception {
        String slug = courseOwnedBy(registerInstructor());
        TestUser owner = registerUser();
        TestUser bo = registerUser();
        enroll(owner, slug);
        enroll(bo, slug);
        int group = createGroup(owner, slug, bo);
        int invite = inviteId(bo, group);
        jdbcTemplate.update("update chat_group_invites set expires_at = now() - interval '1 minute' where id = ?", invite);

        mockMvc.perform(as(bo, post(CHAT + "/invites/" + invite + "/accept"))).andExpect(status().isConflict());
        groupService.runMaintenance();
        assertThat(jdbcTemplate.queryForObject("select status from chat_group_invites where id = ?", String.class, invite))
                .isEqualTo("EXPIRED");
        mockMvc.perform(as(registerUser(), post(CHAT + "/invites/" + invite + "/accept"))).andExpect(status().isNotFound());
    }

    @Test
    void declining_closesTheInvite_andBlockingStopsInvites() throws Exception {
        String slug = courseOwnedBy(registerInstructor());
        TestUser owner = registerUser();
        TestUser bo = registerUser();
        TestUser cy = registerUser();
        for (TestUser user : List.of(owner, bo, cy)) {
            enroll(user, slug);
        }
        int group = createGroup(owner, slug, bo);
        mockMvc.perform(as(bo, post(CHAT + "/invites/" + inviteId(bo, group) + "/decline"))).andExpect(status().isOk());
        mockMvc.perform(as(bo, get(CHAT + "/invites"))).andExpect(jsonPath("$.data", hasSize(0)));

        mockMvc.perform(as(cy, put(CHAT + "/blocks/" + id(owner)))).andExpect(status().isOk());
        mockMvc.perform(asJson(owner, post(CHAT + "/groups/" + group + "/invites"), Map.of("userIds", List.of(id(cy)))))
                .andExpect(jsonPath("$.data.results[0].status").value("unavailable"));
    }

    @Test
    void groupsNotify_membersWhoHaventMutedThem() throws Exception {
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
        mockMvc.perform(asJson(cy, patch(CHAT + "/conversations/" + group), Map.of("muted", true))).andExpect(status().isOk());
        int before = countNotifications(cy, "CHAT_MESSAGE", false);

        send(owner, group, "Meeting at 7");
        mockMvc.perform(as(bo, get("/api/v1/me/notifications")))
                .andExpect(jsonPath("$.data.items[0].title").value("New message in Study group"));
        assertThat(countNotifications(cy, "CHAT_MESSAGE", false)).isEqualTo(before);
    }

    private int activeOwners(int group) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from chat_participants where conversation_id = ? and role = 'OWNER' and left_at is null",
                Integer.class, group);
        return count == null ? 0 : count;
    }
}
