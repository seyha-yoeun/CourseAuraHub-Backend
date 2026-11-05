package com.course_aura_hub.announcement;

import com.course_aura_hub.auth.model.Role;
import com.course_aura_hub.learner.LearnerIntegrationTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AnnouncementIntegrationTest extends LearnerIntegrationTestSupport {

    private static String manage(String slug) {
        return "/api/v1/instructor/courses/" + slug + "/announcements";
    }

    private static String learnerFeed(String slug) {
        return "/api/v1/me/courses/" + slug + "/announcements";
    }

    private static Map<String, String> message(String title) {
        return Map.of("title", title, "body", "Live Q&A on Friday at 7pm.\nBring your questions.");
    }

    @Test
    void enrolledLearnersAreNotifiedAndCanReadTheHistory() throws Exception {
        TestUser admin = registerAdmin();
        String slug = createCourse(admin, "published");
        TestUser learner = registerUser();
        enroll(learner, slug);

        String body = mockMvc.perform(asJson(admin, post(manage(slug)), message("Live session")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("Live session"))
                .andExpect(jsonPath("$.data.authorName").value("Jane Learner"))
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(body, "$.data.id");

        mockMvc.perform(as(learner, get("/api/v1/me/notifications")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].type").value("announcement"))
                .andExpect(jsonPath("$.data.items[0].message").value("Live Q&A on Friday at 7pm. Bring your questions."))
                .andExpect(jsonPath("$.data.items[0].link").value("/learn/" + slug + "?announcement=" + id));

        mockMvc.perform(as(learner, get(learnerFeed(slug))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(id));
    }

    @Test
    void onlyEnrolledLearnersReadAnnouncements() throws Exception {
        String slug = createCourse(registerAdmin(), "published");

        mockMvc.perform(as(registerUser(), get(learnerFeed(slug))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(learnerFeed(slug)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void onlyTheCoursesManagersSendOrDelete() throws Exception {
        TestUser admin = registerAdmin();
        String slug = createCourse(admin, "published");
        TestUser instructor = registerInstructor();
        TestUser learner = registerUser();
        enroll(learner, slug);

        mockMvc.perform(asJson(instructor, post(manage(slug)), message("Not mine")))
                .andExpect(status().isForbidden());
        mockMvc.perform(asJson(learner, post(manage(slug)), message("Learner")))
                .andExpect(status().isForbidden());

        String body = mockMvc.perform(asJson(admin, post(manage(slug)), message("Keep")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(body, "$.data.id");

        mockMvc.perform(as(instructor, delete(manage(slug) + "/" + id)))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(admin, delete(manage(slug) + "/" + id)))
                .andExpect(status().isOk());
        mockMvc.perform(as(admin, get(manage(slug))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void draftsCantAnnounce_andTheDailyLimitHolds() throws Exception {
        TestUser admin = registerAdmin();
        mockMvc.perform(asJson(admin, post(manage(createCourse(admin, "draft"))), message("Too early")))
                .andExpect(status().isBadRequest());

        String slug = createCourse(admin, "published");
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(asJson(admin, post(manage(slug)), message("Update " + i)))
                    .andExpect(status().isCreated());
        }
        mockMvc.perform(asJson(admin, post(manage(slug)), message("One too many")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void blankMessagesAreRejected() throws Exception {
        TestUser admin = registerAdmin();
        String slug = createCourse(admin, "published");
        mockMvc.perform(asJson(admin, post(manage(slug)), Map.of("title", " ", "body", "")))
                .andExpect(status().isBadRequest());
    }

    private TestUser registerInstructor() throws Exception {
        TestUser user = registerUser();
        userRepository.findByEmail(user.email()).ifPresent(entity -> {
            entity.setRole(Role.ROLE_INSTRUCTOR);
            userRepository.save(entity);
        });
        return user;
    }

    private String createCourse(TestUser admin, String status) throws Exception {
        String body = mockMvc.perform(asJson(admin, post("/api/v1/courses"),
                        Map.of("title", "Announce " + System.nanoTime(), "status", status)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.slug");
    }
}
