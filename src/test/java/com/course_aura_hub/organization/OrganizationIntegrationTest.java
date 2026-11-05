package com.course_aura_hub.organization;

import com.course_aura_hub.learner.LearnerIntegrationTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrganizationIntegrationTest extends LearnerIntegrationTestSupport {

    private record Org(String slug, TestUser admin, TestUser owner) {
    }

    @Test
    void managersInviteMembers_andSeatsEnrollThem() throws Exception {
        Org org = createOrg(2);
        TestUser member = registerUser();
        join(org, member, "member");

        mockMvc.perform(asJson(org.owner(), post(seats(org) + "/" + PAID_COURSE + "/assignments"), Map.of("userId", userId(member))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.used").value(1));
        mockMvc.perform(as(member, get("/api/v1/me/courses")))
                .andExpect(jsonPath("$.data[0].slug").value(PAID_COURSE));
        mockMvc.perform(asJson(org.owner(), post(seats(org) + "/" + PAID_COURSE + "/assignments"), Map.of("userId", userId(member))))
                .andExpect(status().isConflict());

        mockMvc.perform(as(org.owner(), get("/api/v1/me/organizations/" + org.slug() + "/progress")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.userId == '" + userId(member) + "')].courses[0].courseSlug").value(PAID_COURSE));
        mockMvc.perform(as(member, get("/api/v1/me/organizations")))
                .andExpect(jsonPath("$.data[0].role").value("member"));
        // Members see seats but not the member list
        mockMvc.perform(as(member, get("/api/v1/me/organizations/" + org.slug())))
                .andExpect(jsonPath("$.data.seats[0].used").value(1))
                .andExpect(jsonPath("$.data.members.length()").value(0));
    }

    @Test
    void seatLimitsHold_andCannotShrinkBelowUse() throws Exception {
        Org org = createOrg(1);
        TestUser first = registerUser();
        TestUser second = registerUser();
        join(org, first, "member");
        join(org, second, "member");

        mockMvc.perform(asJson(org.owner(), post(seats(org) + "/" + PAID_COURSE + "/assignments"), Map.of("userId", userId(first))))
                .andExpect(status().isOk());
        mockMvc.perform(asJson(org.owner(), post(seats(org) + "/" + PAID_COURSE + "/assignments"), Map.of("userId", userId(second))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(asJson(org.admin(), put("/api/v1/admin/organizations/" + org.slug() + "/seats"),
                        Map.of("courseSlug", PAID_COURSE, "total", 0)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(asJson(org.owner(), post(seats(org) + "/" + PAID_COURSE + "/assignments"), Map.of("userId", userId(registerUser()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rolesAndInvitationsAreEnforced() throws Exception {
        Org org = createOrg(1);
        TestUser member = registerUser();
        join(org, member, "member");

        mockMvc.perform(asJson(member, post("/api/v1/me/organizations/" + org.slug() + "/invites"), Map.of("email", "x@example.com")))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(member, get("/api/v1/me/organizations/" + org.slug() + "/progress"))).andExpect(status().isForbidden());
        mockMvc.perform(as(registerUser(), get("/api/v1/me/organizations/" + org.slug()))).andExpect(status().isNotFound());
        mockMvc.perform(as(org.owner(), delete("/api/v1/me/organizations/" + org.slug() + "/members/" + userId(org.owner()))))
                .andExpect(status().isBadRequest());

        // An invitation only works for the address it was sent to, and only once
        String token = invite(org, uniqueEmail("someone"), "member");
        mockMvc.perform(asJson(registerUser(), post("/api/v1/me/organization-invites/accept"), Map.of("token", token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(asJson(member, post("/api/v1/me/organization-invites/accept"), Map.of("token", "not-a-token")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(as(org.owner(), delete("/api/v1/me/organizations/" + org.slug() + "/members/" + userId(member))))
                .andExpect(status().isOk());
        mockMvc.perform(as(member, get("/api/v1/me/organizations/" + org.slug()))).andExpect(status().isNotFound());
        mockMvc.perform(as(member, post("/api/v1/admin/organizations"))).andExpect(status().isForbidden());
    }

    private Org createOrg(int seats) throws Exception {
        TestUser admin = registerAdmin();
        TestUser owner = registerUser();
        String body = mockMvc.perform(asJson(admin, post("/api/v1/admin/organizations"),
                        Map.of("name", "Acme " + System.nanoTime(), "ownerEmail", owner.email())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("owner"))
                .andReturn().getResponse().getContentAsString();
        String slug = JsonPath.read(body, "$.data.slug");
        mockMvc.perform(asJson(admin, put("/api/v1/admin/organizations/" + slug + "/seats"),
                        Map.of("courseSlug", PAID_COURSE, "total", seats)))
                .andExpect(status().isOk());
        return new Org(slug, admin, owner);
    }

    private String invite(Org org, String email, String role) throws Exception {
        String body = mockMvc.perform(asJson(org.owner(), post("/api/v1/me/organizations/" + org.slug() + "/invites"),
                        Map.of("email", email, "role", role)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String link = JsonPath.read(body, "$.data.inviteUrl");
        assertThat(emailSender.latestTo(email)).hasValueSatisfying(mail -> assertThat(mail.textBody()).contains(link));
        return link.substring(link.indexOf("token=") + "token=".length());
    }

    private void join(Org org, TestUser user, String role) throws Exception {
        String token = invite(org, user.email(), role);
        mockMvc.perform(asJson(user, post("/api/v1/me/organization-invites/accept"), Map.of("token", token)))
                .andExpect(status().isOk());
        mockMvc.perform(asJson(user, post("/api/v1/me/organization-invites/accept"), Map.of("token", token)))
                .andExpect(status().isBadRequest());
    }

    private String seats(Org org) {
        return "/api/v1/me/organizations/" + org.slug() + "/seats";
    }

    private UUID userId(TestUser user) {
        return userRepository.findByEmail(user.email()).orElseThrow().getId();
    }
}
