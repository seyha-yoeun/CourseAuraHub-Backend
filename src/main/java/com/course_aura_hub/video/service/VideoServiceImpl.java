package com.course_aura_hub.video.service;

import com.course_aura_hub.common.exception.BusinessRuleException;
import com.course_aura_hub.common.exception.ForbiddenActionException;
import com.course_aura_hub.common.exception.ResourceNotFoundException;
import com.course_aura_hub.common.security.Actor;
import com.course_aura_hub.course.service.CourseAccess;
import com.course_aura_hub.curriculum.model.Lesson;
import com.course_aura_hub.curriculum.model.LessonType;
import com.course_aura_hub.curriculum.repository.LessonRepository;
import com.course_aura_hub.curriculum.service.LessonAvailability;
import com.course_aura_hub.file.model.FilePurpose;
import com.course_aura_hub.file.service.FileService;
import com.course_aura_hub.file.storage.ObjectStore;
import com.course_aura_hub.learner.service.LearnerAccountService;
import com.course_aura_hub.student.repository.StudentCourseRepository;
import com.course_aura_hub.video.config.VideoProperties;
import com.course_aura_hub.video.dto.VideoDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VideoServiceImpl implements VideoService {

    private static final String STORAGE_PREFIX = "storage://";
    private static final Pattern LANGUAGE = Pattern.compile("[a-z]{2,3}(-[a-z0-9]{2,8})?");
    private static final int MAX_PLAYLIST_BYTES = 1024 * 1024;
    private static final int MAX_CAPTION_BYTES = 2 * 1024 * 1024;
    private static final Pattern CUE_TIMING = Pattern.compile("^\\s*(\\d{2}:)?\\d{2}:\\d{2}\\.\\d{3}\\s+-->.*$");
    private static final Pattern TAG = Pattern.compile("<[^>]+>");

    private final LessonRepository lessonRepository;
    private final CourseAccess courseAccess;
    private final LessonAvailability lessonAvailability;
    private final LearnerAccountService learnerAccountService;
    private final StudentCourseRepository studentCourseRepository;
    private final FileService fileService;
    private final ObjectStore objectStore;
    private final JdbcTemplate jdbcTemplate;
    private final VideoProperties properties;

    @Override
    @Transactional(readOnly = true)
    public VideoDtos.Media media(Actor actor, Integer lessonId) {
        Lesson lesson = watchable(actor, lessonId);
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select video_status, hls_manifest_ref, duration_seconds from lessons where id = ?", lessonId);
        String status = (String) row.get("video_status");
        boolean ready = "READY".equals(status) && row.get("hls_manifest_ref") != null;
        return new VideoDtos.Media(
                lesson.getId(),
                status == null ? null : status.toLowerCase(Locale.ROOT),
                ready ? "/api/v1/me/lessons/" + lessonId + "/stream.m3u8" : null,
                row.get("duration_seconds") == null ? null : ((Number) row.get("duration_seconds")).intValue(),
                captions(lessonId));
    }

    @Override
    @Transactional(readOnly = true)
    public String playlist(Actor actor, Integer lessonId) {
        watchable(actor, lessonId);
        List<String> refs = jdbcTemplate.queryForList(
                "select hls_manifest_ref from lessons where id = ? and video_status = 'READY'", String.class, lessonId);
        if (refs.isEmpty() || refs.getFirst() == null) {
            throw new ResourceNotFoundException("This lesson has no stream yet");
        }
        String key = refs.getFirst().substring(STORAGE_PREFIX.length());
        String folder = key.substring(0, key.lastIndexOf('/') + 1);
        String playlist = new String(objectStore.read(key, MAX_PLAYLIST_BYTES), StandardCharsets.UTF_8);

        // Segments are private like the lesson, so each is handed out as its own signed link
        return playlist.lines()
                .map(line -> line.isBlank() || line.startsWith("#") || line.contains("://")
                        ? line
                        : objectStore.presignDownload(folder + line.trim(), properties.linkTtl()))
                .collect(Collectors.joining("\n", "", "\n"));
    }

    @Override
    @Transactional(readOnly = true)
    public VideoDtos.Transcript transcript(Actor actor, Integer lessonId, String language) {
        watchable(actor, lessonId);
        String lang = normalizeLanguage(language);
        List<String> refs = jdbcTemplate.queryForList(
                "select file_ref from lesson_captions where lesson_id = ? and language = ?", String.class, lessonId, lang);
        if (refs.isEmpty()) {
            throw new ResourceNotFoundException("This lesson has no captions in " + lang);
        }
        byte[] vtt = objectStore.read(refs.getFirst().substring(STORAGE_PREFIX.length()), MAX_CAPTION_BYTES);
        return new VideoDtos.Transcript(lessonId, lang, toText(new String(vtt, StandardCharsets.UTF_8)));
    }

    @Override
    @Transactional
    public VideoDtos.Caption saveCaption(Actor actor, String courseSlug, Integer lessonId, String language,
                                         VideoDtos.CaptionRequest request) {
        Lesson lesson = managedVideo(actor, courseSlug, lessonId);
        String lang = normalizeLanguage(language);
        String ref = request.fileRef().trim();
        // Without this, a caption could point at someone else's private file and get it signed
        fileService.requireOwned(ref, actor.email(), FilePurpose.LESSON_CAPTION);
        jdbcTemplate.update("""
                insert into lesson_captions (lesson_id, language, label, file_ref, created_by) values (?, ?, ?, ?, ?)
                on conflict (lesson_id, language) do update set label = excluded.label, file_ref = excluded.file_ref,
                    created_by = excluded.created_by, created_at = now()
                """, lesson.getId(), lang, request.label().trim(), ref, actor.userId());
        return new VideoDtos.Caption(lang, request.label().trim(), fileService.playableUrl(ref));
    }

    @Override
    @Transactional
    public void deleteCaption(Actor actor, String courseSlug, Integer lessonId, String language) {
        managedVideo(actor, courseSlug, lessonId);
        jdbcTemplate.update("delete from lesson_captions where lesson_id = ? and language = ?",
                lessonId, normalizeLanguage(language));
    }

    private List<VideoDtos.Caption> captions(Integer lessonId) {
        return jdbcTemplate.query("select language, label, file_ref from lesson_captions where lesson_id = ? order by language",
                (rs, i) -> new VideoDtos.Caption(rs.getString("language"), rs.getString("label"),
                        fileService.playableUrl(rs.getString("file_ref"))), lessonId);
    }

    /** Free previews, the course's staff, and enrolled learners the lesson has opened for. Others read as forbidden. */
    private Lesson watchable(Actor actor, Integer lessonId) {
        Lesson lesson = lessonRepository.findDetailedById(lessonId)
                .orElseThrow(() -> ResourceNotFoundException.of("Lesson", lessonId));
        if (lesson.getType() != LessonType.VIDEO) {
            throw new BusinessRuleException("This lesson is not a video");
        }
        if (lesson.isPreview() || courseAccess.canManage(lesson.getSection().getCourse(), actor)) {
            return lesson;
        }
        Integer studentId = learnerAccountService.findCurrentStudent(actor.email())
                .filter(student -> studentCourseRepository.existsByStudentIdAndCourseId(
                        student.getId(), lesson.getSection().getCourse().getId()))
                .map(student -> student.getId())
                .orElseThrow(() -> new ForbiddenActionException("Enroll in this course to watch this lesson"));
        lessonAvailability.requireAvailable(lesson, studentId);
        return lesson;
    }

    private Lesson managedVideo(Actor actor, String courseSlug, Integer lessonId) {
        Lesson lesson = lessonRepository.findDetailedById(lessonId)
                .filter(found -> found.getSection().getCourse().getSlug().equals(courseSlug))
                .orElseThrow(() -> ResourceNotFoundException.of("Lesson", lessonId));
        courseAccess.requireManager(lesson.getSection().getCourse(), actor);
        if (lesson.getType() != LessonType.VIDEO) {
            throw new BusinessRuleException("Captions are for video lessons");
        }
        return lesson;
    }

    private static String normalizeLanguage(String language) {
        String lang = language == null ? "" : language.trim().toLowerCase(Locale.ROOT);
        if (!LANGUAGE.matcher(lang).matches()) {
            throw new BusinessRuleException("Language must be a code such as en or km");
        }
        return lang;
    }

    /** The spoken text of a WebVTT file: only the lines after each cue's timing, without markup. */
    static String toText(String vtt) {
        StringBuilder text = new StringBuilder();
        String previous = null;
        for (String block : vtt.replace("\uFEFF", "").split("\\R\\s*\\R")) {
            List<String> lines = block.lines().map(String::trim).toList();
            int timing = -1;
            for (int i = 0; i < lines.size(); i++) {
                if (CUE_TIMING.matcher(lines.get(i)).matches()) {
                    timing = i;
                    break;
                }
            }
            // Header, NOTE, STYLE and REGION blocks have no timing line
            if (timing < 0) {
                continue;
            }
            for (String line : lines.subList(timing + 1, lines.size())) {
                String spoken = TAG.matcher(line).replaceAll("").trim();
                // Rolling captions often repeat the last line; keep it once
                if (!spoken.isEmpty() && !spoken.equals(previous)) {
                    text.append(text.isEmpty() ? "" : " ").append(spoken);
                    previous = spoken;
                }
            }
        }
        return text.toString();
    }
}
