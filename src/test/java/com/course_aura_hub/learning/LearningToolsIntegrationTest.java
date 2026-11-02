package com.course_aura_hub.learning;

import com.course_aura_hub.learner.LearnerIntegrationTestSupport;
import com.course_aura_hub.reminder.service.ReminderService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LearningToolsIntegrationTest extends LearnerIntegrationTestSupport {

    @Autowired
    private ReminderService reminderService;

    private int firstLesson(TestUser user) throws Exception {
        String body = mockMvc.perform(as(user, get("/api/v1/courses/" + FREE_COURSE + "/curriculum")))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.sections[0].lessons[0].id");
    }

    private String questions(int lesson) {
        return "/api/v1/me/lessons/" + lesson + "/questions";
    }

    private int ask(TestUser user, int lesson) throws Exception {
        String body = mockMvc.perform(asJson(user, post(questions(lesson)), Map.of("body", "  How does rebase work?  ")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.body").value("How does rebase work?"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.id");
    }

    @Test
    void questions_areOpenToEnrolledLearnersOnly() throws Exception {
        TestUser learner = registerUser();
        TestUser outsider = registerUser();
        enroll(learner, FREE_COURSE);
        int lesson = firstLesson(learner);

        ask(learner, lesson);

        mockMvc.perform(as(outsider, get(questions(lesson)))).andExpect(status().isForbidden());
        mockMvc.perform(asJson(outsider, post(questions(lesson)), Map.of("body", "Hello")))
                .andExpect(status().isForbidden());
    }

    @Test
    void answers_notifyTheAuthorAndMarkStaff() throws Exception {
        TestUser learner = registerUser();
        TestUser admin = registerAdmin();
        enroll(learner, FREE_COURSE);
        int lesson = firstLesson(learner);
        int question = ask(learner, lesson);

        mockMvc.perform(asJson(admin, post("/api/v1/me/questions/" + question + "/answers"), Map.of("body", "See chapter 2")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.fromStaff").value(true));

        mockMvc.perform(as(learner, get(questions(lesson))))
                .andExpect(jsonPath("$.data[0].answers[0].body").value("See chapter 2"));
        mockMvc.perform(as(learner, get("/api/v1/me/notifications")))
                .andExpect(jsonPath("$.data.items[?(@.type == 'lesson_question')]").isNotEmpty());
    }

    @Test
    void onlyTheAuthorOrStaffChangeAQuestion() throws Exception {
        TestUser learner = registerUser();
        TestUser classmate = registerUser();
        enroll(learner, FREE_COURSE);
        enroll(classmate, FREE_COURSE);
        int question = ask(learner, firstLesson(learner));

        mockMvc.perform(as(classmate, delete("/api/v1/me/questions/" + question))).andExpect(status().isForbidden());
        mockMvc.perform(asJson(learner, patch("/api/v1/me/questions/" + question + "/resolved"), Map.of("resolved", true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resolved").value(true));
        mockMvc.perform(as(learner, delete("/api/v1/me/questions/" + question))).andExpect(status().isOk());
    }

    @Test
    void deletingAnAnswerRemovesIt() throws Exception {
        TestUser learner = registerUser();
        enroll(learner, FREE_COURSE);
        int lesson = firstLesson(learner);
        int question = ask(learner, lesson);
        String body = mockMvc.perform(asJson(learner, post("/api/v1/me/questions/" + question + "/answers"), Map.of("body", "Solved it")))
                .andReturn().getResponse().getContentAsString();
        int answer = JsonPath.read(body, "$.data.id");

        mockMvc.perform(as(learner, delete("/api/v1/me/answers/" + answer))).andExpect(status().isOk());

        mockMvc.perform(as(learner, get(questions(lesson)))).andExpect(jsonPath("$.data[0].answers").isEmpty());
    }

    @Test
    void notes_arePrivateAndBookmarksNeedNoText() throws Exception {
        TestUser learner = registerUser();
        TestUser other = registerUser();
        enroll(learner, FREE_COURSE);
        int lesson = firstLesson(learner);
        String notes = "/api/v1/me/lessons/" + lesson + "/notes";

        String body = mockMvc.perform(asJson(learner, post(notes), Map.of("positionSeconds", 42)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int note = JsonPath.read(body, "$.data.id");

        mockMvc.perform(asJson(learner, post(notes), Map.of("body", " "))).andExpect(status().isBadRequest());
        mockMvc.perform(asJson(other, post(notes), Map.of("body", "Mine"))).andExpect(status().isForbidden());
        mockMvc.perform(as(other, delete("/api/v1/me/notes/" + note))).andExpect(status().isNotFound());
        mockMvc.perform(asJson(learner, put("/api/v1/me/notes/" + note), Map.of("body", "Key point", "positionSeconds", 42)))
                .andExpect(jsonPath("$.data.body").value("Key point"));
        mockMvc.perform(as(learner, get(notes))).andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void categories_arePublicToReadAndProtectedWhileInUse() throws Exception {
        TestUser admin = registerAdmin();
        String name = "Data " + System.nanoTime();

        mockMvc.perform(get("/api/v1/categories")).andExpect(status().isOk());
        mockMvc.perform(asJson(registerUser(), post("/api/v1/admin/categories"), Map.of("name", name)))
                .andExpect(status().isForbidden());
        String body = mockMvc.perform(asJson(admin, post("/api/v1/admin/categories"), Map.of("name", name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(body, "$.data.id");
        mockMvc.perform(asJson(admin, post("/api/v1/admin/categories"), Map.of("name", name.toUpperCase())))
                .andExpect(status().isConflict());

        String used = jdbcTemplate.queryForObject("select category from courses where slug = ?", String.class, FREE_COURSE);
        Integer usedId = jdbcTemplate.queryForObject("select id from categories where name = ?", Integer.class, used);
        mockMvc.perform(as(admin, delete("/api/v1/admin/categories/" + usedId))).andExpect(status().isBadRequest());
        mockMvc.perform(as(admin, delete("/api/v1/admin/categories/" + id))).andExpect(status().isOk());
    }

    @Test
    void renamingACategoryMovesItsCourses() throws Exception {
        TestUser admin = registerAdmin();
        String oldName = "Old " + System.nanoTime();
        String newName = "New " + System.nanoTime();
        String body = mockMvc.perform(asJson(admin, post("/api/v1/admin/categories"), Map.of("name", oldName)))
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(body, "$.data.id");
        String original = jdbcTemplate.queryForObject("select category from courses where slug = ?", String.class, FREE_COURSE);
        jdbcTemplate.update("update courses set category = ? where slug = ?", oldName, FREE_COURSE);

        mockMvc.perform(asJson(admin, put("/api/v1/admin/categories/" + id), Map.of("name", newName)))
                .andExpect(jsonPath("$.data.courseCount").value(1));

        assertThat(jdbcTemplate.queryForObject("select category from courses where slug = ?", String.class, FREE_COURSE))
                .isEqualTo(newName);
        jdbcTemplate.update("update courses set category = ? where slug = ?", original, FREE_COURSE);
    }

    @Test
    void reminders_nudgeQuietLearnersOnceAndRespectOptOut() throws Exception {
        TestUser quiet = registerUser();
        TestUser optedOut = registerUser();
        enroll(quiet, FREE_COURSE);
        enroll(optedOut, FREE_COURSE);
        jdbcTemplate.update("update enrollments set enrolled_at = current_date - 30 where student_id in "
                + "(select id from students where email in (?, ?))", quiet.email(), optedOut.email());
        mockMvc.perform(asJson(optedOut, put("/api/v1/me/reminders"), Map.of("enabled", false)))
                .andExpect(jsonPath("$.data.enabled").value(false));

        reminderService.sendDue();
        reminderService.sendDue();

        assertThat(remindersFor(quiet)).isEqualTo(1);
        assertThat(remindersFor(optedOut)).isZero();
    }

    private long remindersFor(TestUser user) {
        Long count = jdbcTemplate.queryForObject("""
                select count(*) from notifications n join users u on u.id = n.user_id
                where u.email = ? and n.type = 'COURSE_REMINDER'
                """, Long.class, user.email());
        return count == null ? 0 : count;
    }
}
