package com.course_aura_hub.instructor;

import com.course_aura_hub.auth.model.Role;
import com.course_aura_hub.learner.LearnerIntegrationTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InstructorIntegrationTest extends LearnerIntegrationTestSupport {

    private static final String COURSES = "/api/v1/instructor/courses";

    private TestUser registerInstructor() throws Exception {
        TestUser user = registerUser();
        userRepository.findByEmail(user.email()).ifPresent(entity -> {
            entity.setRole(Role.ROLE_INSTRUCTOR);
            userRepository.save(entity);
        });
        return user;
    }

    private String createCourse(TestUser instructor) throws Exception {
        String body = mockMvc.perform(asJson(instructor, post(COURSES), Map.of(
                        "title", "Teach " + System.nanoTime(), "status", "published", "instructor", "Someone Else")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.course.status").value("draft"))
                .andExpect(jsonPath("$.data.course.instructor").value("Jane Learner"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.course.slug");
    }

    private int addLesson(TestUser instructor, String slug) throws Exception {
        String section = mockMvc.perform(asJson(instructor, post("/api/v1/courses/" + slug + "/sections"), Map.of("title", "One")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int sectionId = JsonPath.read(section, "$.data.id");
        String lesson = mockMvc.perform(asJson(instructor, post("/api/v1/courses/" + slug + "/sections/" + sectionId + "/lessons"),
                        Map.of("title", "Intro", "type", "article", "durationMinutes", 5)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(lesson, "$.data.id");
    }

    private ResultActions submit(TestUser instructor, String slug) throws Exception {
        return mockMvc.perform(as(instructor, post(COURSES + "/" + slug + "/submit")));
    }

    private String publishedCourse(TestUser instructor, TestUser admin) throws Exception {
        String slug = createCourse(instructor);
        addLesson(instructor, slug);
        submit(instructor, slug).andExpect(status().isOk());
        mockMvc.perform(as(admin, post("/api/v1/admin/course-reviews/" + slug + "/approve"))).andExpect(status().isOk());
        return slug;
    }

    @Test
    void learnersCantReachTheWorkspace() throws Exception {
        mockMvc.perform(as(registerUser(), get(COURSES))).andExpect(status().isForbidden());
    }

    @Test
    void instructorsOnlyManageTheirOwnCourses() throws Exception {
        TestUser owner = registerInstructor();
        TestUser other = registerInstructor();
        String slug = createCourse(owner);

        mockMvc.perform(as(other, get(COURSES + "/" + slug))).andExpect(status().isForbidden());
        mockMvc.perform(asJson(other, post("/api/v1/courses/" + slug + "/sections"), Map.of("title", "Mine now")))
                .andExpect(status().isForbidden());
        mockMvc.perform(asJson(other, post("/api/v1/courses/" + FREE_COURSE + "/sections"), Map.of("title", "Seeded")))
                .andExpect(status().isForbidden());
        mockMvc.perform(asJson(owner, put("/api/v1/courses/" + slug), Map.of("title", "Admin route")))
                .andExpect(status().isForbidden());
    }

    @Test
    void anInstructorCantAttachSomeoneElsesPrivateFile() throws Exception {
        TestUser instructor = registerInstructor();
        String slug = createCourse(instructor);
        int lesson = addLesson(instructor, slug);

        mockMvc.perform(asJson(instructor, put("/api/v1/courses/" + slug + "/lessons/" + lesson),
                        Map.of("contentUrl", "storage://private/lessons/someone-elses.mp4")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aCourseGoesLiveOnlyThroughApproval() throws Exception {
        TestUser instructor = registerInstructor();
        TestUser admin = registerAdmin();
        TestUser learner = registerUser();
        String slug = createCourse(instructor);

        submit(instructor, slug).andExpect(status().isBadRequest());
        addLesson(instructor, slug);
        mockMvc.perform(asJson(instructor, put(COURSES + "/" + slug), Map.of("status", "published", "title", "Better")))
                .andExpect(jsonPath("$.data.course.status").value("draft"))
                .andExpect(jsonPath("$.data.course.title").value("Better"));
        mockMvc.perform(get("/api/v1/courses/" + slug)).andExpect(status().isNotFound());

        submit(instructor, slug).andExpect(jsonPath("$.data.course.status").value("pending_review"));
        mockMvc.perform(asJson(instructor, put(COURSES + "/" + slug), Map.of("title", "Sneaky")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(learner, get("/api/v1/courses/" + slug))).andExpect(status().isNotFound());

        mockMvc.perform(asJson(admin, post("/api/v1/admin/course-reviews/" + slug + "/reject"), Map.of("note", "Add a summary")))
                .andExpect(jsonPath("$.data.status").value("draft"));
        mockMvc.perform(as(instructor, get(COURSES + "/" + slug)))
                .andExpect(jsonPath("$.data.reviewNote").value("Add a summary"));

        submit(instructor, slug).andExpect(status().isOk());
        mockMvc.perform(as(admin, post("/api/v1/admin/course-reviews/" + slug + "/approve")))
                .andExpect(jsonPath("$.data.status").value("published"));
        mockMvc.perform(get("/api/v1/courses/" + slug)).andExpect(status().isOk());
    }

    @Test
    void adminsSetTheInstructorRoleButNeverAdmin() throws Exception {
        TestUser admin = registerAdmin();
        TestUser learner = registerUser();
        String userId = userRepository.findByEmail(learner.email()).orElseThrow().getId().toString();
        String role = "/api/v1/admin/users/" + userId + "/role";

        mockMvc.perform(asJson(learner, patch(role), Map.of("role", "INSTRUCTOR"))).andExpect(status().isForbidden());
        mockMvc.perform(asJson(admin, patch(role), Map.of("role", "ADMIN"))).andExpect(status().is4xxClientError());
        mockMvc.perform(asJson(admin, patch(role), Map.of("role", "INSTRUCTOR")))
                .andExpect(jsonPath("$.data.role").value("ROLE_INSTRUCTOR"));
        mockMvc.perform(as(learner, get(COURSES))).andExpect(status().isOk());
    }

    @Test
    void courseStudentsHideContactDetails() throws Exception {
        TestUser instructor = registerInstructor();
        TestUser admin = registerAdmin();
        TestUser learner = registerUser();
        String slug = publishedCourse(instructor, admin);
        enroll(learner, slug);

        mockMvc.perform(as(instructor, get(COURSES + "/" + slug + "/students")))
                .andExpect(jsonPath("$.data[0].name").value("Jane Learner"))
                .andExpect(jsonPath("$.data[0].email").doesNotExist());
    }

    @Test
    void anAssignmentGateCompletesOnAPassingGrade() throws Exception {
        TestUser instructor = registerInstructor();
        TestUser admin = registerAdmin();
        TestUser learner = registerUser();
        String slug = createCourse(instructor);
        int lesson = addLesson(instructor, slug);
        String assignment = "/api/v1/instructor/lessons/" + lesson + "/assignment";
        mockMvc.perform(asJson(instructor, put(assignment), Map.of("instructions", "Build it", "passScore", 60, "required", true)))
                .andExpect(status().isOk());
        submit(instructor, slug).andExpect(status().isOk());
        mockMvc.perform(as(admin, post("/api/v1/admin/course-reviews/" + slug + "/approve"))).andExpect(status().isOk());
        enroll(learner, slug);
        String mine = "/api/v1/me/lessons/" + lesson;

        mockMvc.perform(as(learner, put(mine + "/completion"))).andExpect(status().isBadRequest());
        mockMvc.perform(asJson(learner, post(mine + "/assignment/submission"), Map.of("comment", "Done")))
                .andExpect(jsonPath("$.data.submission.status").value("SUBMITTED"));
        mockMvc.perform(asJson(learner, post(mine + "/assignment/submission"), Map.of()))
                .andExpect(status().isBadRequest());

        String queue = mockMvc.perform(as(instructor, get("/api/v1/instructor/submissions")))
                .andExpect(jsonPath("$.data.items[0].studentName").value("Jane Learner"))
                .andReturn().getResponse().getContentAsString();
        int submission = JsonPath.read(queue, "$.data.items[0].submission.id");
        String grade = "/api/v1/instructor/submissions/" + submission + "/grade";

        mockMvc.perform(asJson(registerInstructor(), post(grade), Map.of("score", 100))).andExpect(status().isForbidden());
        mockMvc.perform(asJson(instructor, post(grade), Map.of("score", 40, "feedback", "Try again")))
                .andExpect(jsonPath("$.data.submission.passed").value(false));
        mockMvc.perform(as(learner, put(mine + "/completion"))).andExpect(status().isBadRequest());

        mockMvc.perform(asJson(instructor, post(grade), Map.of("score", 80)))
                .andExpect(jsonPath("$.data.submission.passed").value(true));
        mockMvc.perform(as(learner, get("/api/v1/courses/" + slug + "/curriculum")))
                .andExpect(jsonPath("$.data.sections[0].lessons[0].completed").value(true));
        mockMvc.perform(asJson(learner, post(mine + "/assignment/submission"), Map.of("comment", "Again")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submissionsCantPointAtAnotherPersonsFile() throws Exception {
        TestUser instructor = registerInstructor();
        TestUser admin = registerAdmin();
        TestUser learner = registerUser();
        String slug = createCourse(instructor);
        int lesson = addLesson(instructor, slug);
        mockMvc.perform(asJson(instructor, put("/api/v1/instructor/lessons/" + lesson + "/assignment"),
                Map.of("instructions", "Build it", "passScore", 60, "required", false))).andExpect(status().isOk());
        submit(instructor, slug).andExpect(status().isOk());
        mockMvc.perform(as(admin, post("/api/v1/admin/course-reviews/" + slug + "/approve"))).andExpect(status().isOk());
        enroll(learner, slug);

        mockMvc.perform(asJson(learner, post("/api/v1/me/lessons/" + lesson + "/assignment/submission"),
                        Map.of("fileUrl", "storage://private/lessons/paid.mp4")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anInstructorDeletesOnlyTheirEmptyDraft() throws Exception {
        TestUser instructor = registerInstructor();
        TestUser admin = registerAdmin();
        String draft = createCourse(instructor);
        String live = publishedCourse(instructor, admin);

        mockMvc.perform(as(instructor, delete(COURSES + "/" + live))).andExpect(status().isBadRequest());
        mockMvc.perform(as(instructor, delete(COURSES + "/" + draft))).andExpect(status().isOk());
    }
}
