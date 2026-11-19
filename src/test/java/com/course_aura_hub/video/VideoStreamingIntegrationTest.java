package com.course_aura_hub.video;

import com.course_aura_hub.file.storage.ObjectStore;
import com.course_aura_hub.learner.LearnerIntegrationTestSupport;
import com.course_aura_hub.video.service.TranscodeQueue;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class VideoStreamingIntegrationTest extends LearnerIntegrationTestSupport {

    private static final String PLAYLIST = "#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6.0,\nsegment_00000.ts\n#EXT-X-ENDLIST\n";

    @MockitoBean
    private ObjectStore objectStore;
    @Autowired
    private TranscodeQueue queue;

    @BeforeEach
    void stubStorage() {
        reset(objectStore);
        when(objectStore.presignDownload(anyString(), any())).thenAnswer(call -> "https://signed.example/" + call.getArgument(0));
    }

    @Test
    void enrolledLearnersGetASignedPlaylist_othersDont() throws Exception {
        TestUser admin = registerAdmin();
        Lesson lesson = videoLesson(admin);
        String playlistKey = "private/hls/" + lesson.id() + "/1/index.m3u8";
        jdbcTemplate.update("""
                update lessons set video_status = 'READY', hls_manifest_ref = ?, duration_seconds = 95 where id = ?
                """, "storage://" + playlistKey, lesson.id());
        when(objectStore.read(eq(playlistKey), anyInt())).thenReturn(PLAYLIST.getBytes(StandardCharsets.UTF_8));

        TestUser learner = registerUser();
        enroll(learner, lesson.slug());
        mockMvc.perform(as(learner, get("/api/v1/me/lessons/" + lesson.id() + "/media")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ready"))
                .andExpect(jsonPath("$.data.streamUrl").value("/api/v1/me/lessons/" + lesson.id() + "/stream.m3u8"))
                .andExpect(jsonPath("$.data.durationSeconds").value(95));
        mockMvc.perform(as(learner, get("/api/v1/me/lessons/" + lesson.id() + "/stream.m3u8")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "https://signed.example/private/hls/" + lesson.id() + "/1/segment_00000.ts")))
                .andExpect(content().string(containsString("#EXTINF:6.0,")))
                .andExpect(content().string(not(containsString("\nsegment_00000.ts"))));

        TestUser outsider = registerUser();
        mockMvc.perform(as(outsider, get("/api/v1/me/lessons/" + lesson.id() + "/media"))).andExpect(status().isForbidden());
        mockMvc.perform(as(outsider, get("/api/v1/me/lessons/" + lesson.id() + "/stream.m3u8"))).andExpect(status().isForbidden());
    }

    @Test
    void staffAddCaptions_fromTheirOwnUpload_andLearnersReadTheTranscript() throws Exception {
        TestUser admin = registerAdmin();
        Lesson lesson = videoLesson(admin);
        String captionKey = "private/captions/" + UUID.randomUUID() + ".vtt";
        jdbcTemplate.update("""
                insert into stored_files (id, uploaded_by, purpose, object_key, content_type, size_bytes, original_name, status)
                values (?, ?, 'LESSON_CAPTION', ?, 'text/vtt', 100, 'en.vtt', 'READY')
                """, UUID.randomUUID(), userId(admin.email()), captionKey);
        when(objectStore.read(eq(captionKey), anyInt())).thenReturn("""
                WEBVTT

                00:00:01.000 --> 00:00:03.000
                Hello and welcome.
                """.getBytes(StandardCharsets.UTF_8));

        String path = "/api/v1/courses/" + lesson.slug() + "/lessons/" + lesson.id() + "/captions/en";
        mockMvc.perform(asJson(admin, put(path), Map.of("fileRef", "storage://private/captions/not-mine.vtt", "label", "English")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(asJson(admin, put(path), Map.of("fileRef", "storage://" + captionKey, "label", "English")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.url").value("https://signed.example/" + captionKey));

        TestUser learner = registerUser();
        enroll(learner, lesson.slug());
        mockMvc.perform(as(learner, get("/api/v1/me/lessons/" + lesson.id() + "/media")))
                .andExpect(jsonPath("$.data.captions[0].language").value("en"))
                .andExpect(jsonPath("$.data.streamUrl").doesNotExist());
        mockMvc.perform(as(learner, get("/api/v1/me/lessons/" + lesson.id() + "/transcript")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.text").value("Hello and welcome."));
        mockMvc.perform(asJson(learner, put(path), Map.of("fileRef", "storage://" + captionKey, "label", "English")))
                .andExpect(status().isForbidden());
    }

    @Test
    void theQueuePicksUpNewUploads_andAFinishedTranscodeSetsTheDuration() throws Exception {
        TestUser admin = registerAdmin();
        Lesson lesson = videoLesson(admin);

        queue.enqueueNew();
        String videoStatus = jdbcTemplate.queryForObject("select video_status from lessons where id = ?", String.class, lesson.id());
        assertThat(videoStatus).isEqualTo("PROCESSING");

        TranscodeQueue.Job job = null;
        for (TranscodeQueue.Job claimed = queue.claimNext().orElse(null); claimed != null; claimed = queue.claimNext().orElse(null)) {
            if (claimed.lessonId().equals(lesson.id())) {
                job = claimed;
                break;
            }
            queue.fail(claimed, "Not part of this test");
        }
        assertThat(job).isNotNull();
        queue.complete(job, "private/hls/" + lesson.id() + "/" + job.id() + "/index.m3u8", 125);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select video_status, duration_seconds, duration_minutes, video_source_ref, content_url from lessons where id = ?",
                lesson.id());
        assertThat(row.get("video_status")).isEqualTo("READY");
        assertThat(((Number) row.get("duration_seconds")).intValue()).isEqualTo(125);
        assertThat(((Number) row.get("duration_minutes")).intValue()).isEqualTo(3);
        assertThat(row.get("video_source_ref")).isEqualTo(row.get("content_url"));

        // Nothing new to do for the same file
        queue.enqueueNew();
        Integer open = jdbcTemplate.queryForObject("""
                select count(*) from video_transcode_jobs where lesson_id = ? and status in ('QUEUED', 'RUNNING')
                """, Integer.class, lesson.id());
        assertThat(open).isZero();
    }

    private record Lesson(String slug, int id) {
    }

    private Lesson videoLesson(TestUser admin) throws Exception {
        String body = mockMvc.perform(asJson(admin, post("/api/v1/courses"),
                        Map.of("title", "Video " + System.nanoTime(), "status", "published")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String slug = JsonPath.read(body, "$.data.slug");
        String section = mockMvc.perform(asJson(admin, post("/api/v1/courses/" + slug + "/sections"), Map.of("title", "Basics")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int sectionId = JsonPath.read(section, "$.data.id");
        String lesson = mockMvc.perform(asJson(admin, post("/api/v1/courses/" + slug + "/sections/" + sectionId + "/lessons"),
                        Map.of("title", "Watch me", "type", "video", "durationMinutes", 5)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(lesson, "$.data.id");
        jdbcTemplate.update("update lessons set content_url = ? where id = ?",
                "storage://private/lessons/" + UUID.randomUUID() + ".mp4", id);
        return new Lesson(slug, id);
    }
}
