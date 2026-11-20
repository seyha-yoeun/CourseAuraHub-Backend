package com.course_aura_hub.live;

import com.course_aura_hub.learner.LearnerIntegrationTestSupport;
import com.course_aura_hub.live.service.LiveSessionService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LiveSessionIntegrationTest extends LearnerIntegrationTestSupport {

    @Autowired
    private LiveSessionService liveSessionService;

    @Test
    void learnersJoinWithinTheWindow_andAttendanceIsRecorded() throws Exception {
        TestUser admin = registerAdmin();
        String slug = createCourse(admin);
        TestUser learner = registerUser();
        enroll(learner, slug);

        int id = schedule(admin, slug, Instant.now().plus(5, ChronoUnit.MINUTES), "https://meet.example.com/abc");
        mockMvc.perform(as(learner, get("/api/v1/me/notifications")))
                .andExpect(jsonPath("$.data.items[0].type").value("live_session"));

        mockMvc.perform(as(learner, get("/api/v1/me/live-sessions")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + id + ")].joinable").value(true))
                .andExpect(jsonPath("$..meetingUrl").doesNotExist());
        mockMvc.perform(as(learner, post("/api/v1/me/live-sessions/" + id + "/join")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.meetingUrl").value("https://meet.example.com/abc"));
        mockMvc.perform(as(learner, get("/api/v1/me/live-sessions")))
                .andExpect(jsonPath("$.data[?(@.id == " + id + ")].attended").value(true));
        mockMvc.perform(as(admin, get(manage(slug))))
                .andExpect(jsonPath("$.data[0].attendees").value(1));

        // Someone not enrolled can't find it
        mockMvc.perform(as(registerUser(), post("/api/v1/me/live-sessions/" + id + "/join"))).andExpect(status().isNotFound());

        // It starts within ten minutes, so the "starting soon" reminder goes out
        liveSessionService.sendReminders();
        Boolean remindedSoon = jdbcTemplate.queryForObject("select reminded_soon from live_sessions where id = ?",
                Boolean.class, id);
        assertThat(remindedSoon).isTrue();

        mockMvc.perform(as(admin, delete(manage(slug) + "/" + id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cancelled").value(true));
        mockMvc.perform(as(learner, post("/api/v1/me/live-sessions/" + id + "/join"))).andExpect(status().isBadRequest());
    }

    @Test
    void tooEarlyLinksAndPastStartsAreRejected() throws Exception {
        TestUser admin = registerAdmin();
        String slug = createCourse(admin);
        TestUser learner = registerUser();
        enroll(learner, slug);

        mockMvc.perform(asJson(admin, post(manage(slug)),
                        session(Instant.now().plus(1, ChronoUnit.DAYS), "http://meet.example.com/abc")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(asJson(admin, post(manage(slug)),
                        session(Instant.now().minus(1, ChronoUnit.HOURS), "https://meet.example.com/abc")))
                .andExpect(status().isBadRequest());

        int id = schedule(admin, slug, Instant.now().plus(2, ChronoUnit.DAYS), "https://meet.example.com/later");
        mockMvc.perform(as(learner, post("/api/v1/me/live-sessions/" + id + "/join"))).andExpect(status().isBadRequest());
        mockMvc.perform(as(learner, get(manage(slug)))).andExpect(status().isForbidden());
    }

    @Test
    void theCalendarFeedIsPrivate_andRotates() throws Exception {
        TestUser admin = registerAdmin();
        String slug = createCourse(admin);
        TestUser learner = registerUser();
        enroll(learner, slug);
        schedule(admin, slug, Instant.now().plus(3, ChronoUnit.DAYS), "https://meet.example.com/feed");

        String first = feedPath(learner);
        mockMvc.perform(get(first))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("BEGIN:VCALENDAR")))
                .andExpect(content().string(containsString("SUMMARY:Office hours")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("meet.example.com"))));

        String second = feedPath(learner);
        mockMvc.perform(get(first)).andExpect(status().isNotFound());
        mockMvc.perform(get(second)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/calendar/not-a-real-token.ics")).andExpect(status().isNotFound());
    }

    private String feedPath(TestUser user) throws Exception {
        String body = mockMvc.perform(as(user, post("/api/v1/me/calendar-token")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String url = JsonPath.read(body, "$.data.feedUrl");
        return url.substring(url.indexOf("/api/v1/"));
    }

    private int schedule(TestUser admin, String slug, Instant startsAt, String meetingUrl) throws Exception {
        String body = mockMvc.perform(asJson(admin, post(manage(slug)), session(startsAt, meetingUrl)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.id");
    }

    private static Map<String, Object> session(Instant startsAt, String meetingUrl) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", "Office hours");
        body.put("startsAt", startsAt.toString());
        body.put("durationMinutes", 60);
        body.put("meetingUrl", meetingUrl);
        return body;
    }

    private String createCourse(TestUser admin) throws Exception {
        String body = mockMvc.perform(asJson(admin, post("/api/v1/courses"),
                        Map.of("title", "Live " + System.nanoTime(), "status", "published")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.slug");
    }

    private static String manage(String slug) {
        return "/api/v1/instructor/courses/" + slug + "/live-sessions";
    }
}
