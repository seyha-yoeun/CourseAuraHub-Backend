package com.course_aura_hub.instructor;

import com.course_aura_hub.auth.model.Role;
import com.course_aura_hub.learner.LearnerIntegrationTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InstructorProfileIntegrationTest extends LearnerIntegrationTestSupport {

    private static final String PROFILE = "/api/v1/instructor/profile";

    @Test
    void publicPageShowsPublishedCoursesAndNoEmail() throws Exception {
        TestUser instructor = registerInstructor();
        String handle = "teach-" + System.nanoTime();
        mockMvc.perform(asJson(instructor, put(PROFILE), Map.of(
                        "handle", handle,
                        "headline", "Backend engineer",
                        "githubUrl", "https://github.com/someone")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.publicPath").value("/instructors/" + handle));

        String published = publishedCourse(instructor, registerAdmin());
        String draft = createCourse(instructor);

        String body = mockMvc.perform(get("/api/v1/instructors/" + handle))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Jane Learner"))
                .andExpect(jsonPath("$.data.headline").value("Backend engineer"))
                .andExpect(jsonPath("$.data.links.github").value("https://github.com/someone"))
                .andExpect(jsonPath("$.data.stats.courses").value(1))
                .andExpect(jsonPath("$..email").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        List<String> slugs = JsonPath.read(body, "$.data.courses[*].slug");
        assertThat(slugs).containsExactly(published).doesNotContain(draft);

        mockMvc.perform(get("/api/v1/courses/" + published + "/instructor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.handle").value(handle));

        String list = mockMvc.perform(get("/api/v1/instructors").param("limit", "50"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> handles = JsonPath.read(list, "$.data[*].handle");
        assertThat(handles).contains(handle);
    }

    @Test
    void firstVisitCreatesAHandleFromTheName() throws Exception {
        TestUser instructor = registerInstructor();
        mockMvc.perform(as(instructor, get(PROFILE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.handle").value(org.hamcrest.Matchers.startsWith("jane-learner")));
    }

    @Test
    void handlesAreUniqueAndLinksMustBeSafe() throws Exception {
        TestUser first = registerInstructor();
        TestUser second = registerInstructor();
        String handle = "taken-" + System.nanoTime();
        mockMvc.perform(asJson(first, put(PROFILE), Map.of("handle", handle))).andExpect(status().isOk());
        mockMvc.perform(asJson(second, put(PROFILE), Map.of("handle", handle))).andExpect(status().isConflict());

        mockMvc.perform(asJson(second, put(PROFILE), Map.of("handle", "-Bad Handle")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(asJson(second, put(PROFILE), Map.of("linkedinUrl", "javascript:alert(1)")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void profileDisappearsWhenTheAccountCanNoLongerTeach() throws Exception {
        TestUser instructor = registerInstructor();
        String handle = "former-" + System.nanoTime();
        mockMvc.perform(asJson(instructor, put(PROFILE), Map.of("handle", handle))).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/instructors/" + handle)).andExpect(status().isOk());

        userRepository.findByEmail(instructor.email()).ifPresent(entity -> {
            entity.setRole(Role.ROLE_USER);
            userRepository.save(entity);
        });
        mockMvc.perform(get("/api/v1/instructors/" + handle)).andExpect(status().isNotFound());
    }

    @Test
    void learnersCantEditAProfile_andAdminCoursesHaveNoInstructorPage() throws Exception {
        mockMvc.perform(as(registerUser(), get(PROFILE))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/courses/" + FREE_COURSE + "/instructor")).andExpect(status().isNotFound());
    }

    private TestUser registerInstructor() throws Exception {
        TestUser user = registerUser();
        userRepository.findByEmail(user.email()).ifPresent(entity -> {
            entity.setRole(Role.ROLE_INSTRUCTOR);
            userRepository.save(entity);
        });
        return user;
    }

    private String createCourse(TestUser instructor) throws Exception {
        String body = mockMvc.perform(asJson(instructor, post("/api/v1/instructor/courses"),
                        Map.of("title", "Profile " + System.nanoTime())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.course.slug");
    }

    private String publishedCourse(TestUser instructor, TestUser admin) throws Exception {
        String slug = createCourse(instructor);
        String section = mockMvc.perform(asJson(instructor, post("/api/v1/courses/" + slug + "/sections"), Map.of("title", "One")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int sectionId = JsonPath.read(section, "$.data.id");
        mockMvc.perform(asJson(instructor, post("/api/v1/courses/" + slug + "/sections/" + sectionId + "/lessons"),
                        Map.of("title", "Intro", "type", "article", "durationMinutes", 5)))
                .andExpect(status().isCreated());
        mockMvc.perform(as(instructor, post("/api/v1/instructor/courses/" + slug + "/submit"))).andExpect(status().isOk());
        mockMvc.perform(as(admin, post("/api/v1/admin/course-reviews/" + slug + "/approve"))).andExpect(status().isOk());
        return slug;
    }
}
