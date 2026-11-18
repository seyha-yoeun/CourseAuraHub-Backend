package com.course_aura_hub.live.service;

import com.course_aura_hub.auth.security.TokenHasher;
import com.course_aura_hub.common.exception.BusinessRuleException;
import com.course_aura_hub.common.exception.ResourceNotFoundException;
import com.course_aura_hub.common.security.Actor;
import com.course_aura_hub.course.model.Course;
import com.course_aura_hub.course.service.CourseAccess;
import com.course_aura_hub.curriculum.repository.LessonRepository;
import com.course_aura_hub.learner.service.LearnerAccountService;
import com.course_aura_hub.live.config.LiveProperties;
import com.course_aura_hub.live.dto.LiveDtos;
import com.course_aura_hub.live.model.LiveSession;
import com.course_aura_hub.live.repository.LiveSessionRepository;
import com.course_aura_hub.notification.model.NotificationType;
import com.course_aura_hub.notification.service.NotificationService;
import com.course_aura_hub.settings.service.SettingsService;
import com.course_aura_hub.student.model.Student;
import com.course_aura_hub.student.repository.StudentCourseRepository;
import com.course_aura_hub.student.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveSessionServiceImpl implements LiveSessionService {

    private static final String CALENDAR_PURPOSE = "calendar-feed";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter ICS_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final Duration HOUR_REMINDER = Duration.ofHours(1);
    private static final Duration SOON_REMINDER = Duration.ofMinutes(10);
    private static final Duration PAST_SHOWN = Duration.ofDays(7);
    private static final Duration FEED_AHEAD = Duration.ofDays(120);

    private final LiveSessionRepository sessionRepository;
    private final CourseAccess courseAccess;
    private final LearnerAccountService learnerAccountService;
    private final StudentCourseRepository studentCourseRepository;
    private final StudentRepository studentRepository;
    private final LessonRepository lessonRepository;
    private final NotificationService notificationService;
    private final SettingsService settingsService;
    private final TokenHasher tokenHasher;
    private final JdbcTemplate jdbcTemplate;
    private final LiveProperties properties;

    @Override
    @Transactional(readOnly = true)
    public List<LiveDtos.ManagedSession> findForCourse(Actor actor, String courseSlug) {
        Course course = managed(actor, courseSlug);
        return sessionRepository.findByCourseIdOrderByStartsAtAsc(course.getId()).stream().map(this::toManaged).toList();
    }

    @Override
    @Transactional
    public LiveDtos.ManagedSession create(Actor actor, String courseSlug, LiveDtos.SessionRequest request) {
        Course course = managed(actor, courseSlug);
        if (request.startsAt().isBefore(Instant.now())) {
            throw new BusinessRuleException("Schedule the session in the future");
        }
        LiveSession session = LiveSession.builder().course(course).createdBy(actor.userId()).build();
        apply(session, course, request);
        sessionRepository.save(session);
        notifyEnrolled(session, "Live session scheduled",
                "%s: %s on %s (UTC).".formatted(course.getTitle(), session.getTitle(), readable(session.getStartsAt())));
        log.info("Live session scheduled id={} courseId={}", session.getId(), course.getId());
        return toManaged(session);
    }

    @Override
    @Transactional
    public LiveDtos.ManagedSession update(Actor actor, String courseSlug, Integer id, LiveDtos.SessionRequest request) {
        Course course = managed(actor, courseSlug);
        LiveSession session = owned(course, id);
        boolean moved = !session.getStartsAt().equals(request.startsAt());
        apply(session, course, request);
        if (moved) {
            // A new time deserves new reminders
            session.setRemindedHour(false);
            session.setRemindedSoon(false);
            if (session.getStartsAt().isAfter(Instant.now()) && !session.isCancelled()) {
                notifyEnrolled(session, "Live session moved", "%s now starts %s (UTC).".formatted(session.getTitle(),
                        readable(session.getStartsAt())));
            }
        }
        return toManaged(session);
    }

    @Override
    @Transactional
    public LiveDtos.ManagedSession cancel(Actor actor, String courseSlug, Integer id) {
        Course course = managed(actor, courseSlug);
        LiveSession session = owned(course, id);
        if (!session.isCancelled()) {
            session.setCancelled(true);
            if (session.getStartsAt().isAfter(Instant.now())) {
                notifyEnrolled(session, "Live session cancelled", session.getTitle() + " on "
                        + readable(session.getStartsAt()) + " (UTC) is cancelled.");
            }
        }
        return toManaged(session);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LiveDtos.LearnerSession> findMine(String email) {
        Optional<Student> student = learnerAccountService.findCurrentStudent(email);
        if (student.isEmpty()) {
            return List.of();
        }
        Instant now = Instant.now();
        List<LiveSession> sessions = sessionsFor(student.get().getId(), now.minus(PAST_SHOWN), now.plus(FEED_AHEAD));
        Set<Integer> attended = attendedSessions(student.get().getId());
        return sessions.stream().map(session -> new LiveDtos.LearnerSession(
                session.getId(),
                session.getCourse().getSlug(),
                session.getCourse().getTitle(),
                session.getTitle(),
                session.getDescription(),
                session.getStartsAt(),
                session.endsAt(),
                session.isCancelled(),
                joinable(session, now),
                attended.contains(session.getId()),
                session.getRecordingLessonId()
        )).toList();
    }

    @Override
    @Transactional
    public LiveDtos.JoinResponse join(String email, Integer id) {
        LiveSession session = sessionRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Live session", id));
        Student student = learnerAccountService.currentStudent(email);
        if (studentCourseRepository.findByStudentIdAndCourseId(student.getId(), session.getCourse().getId()).isEmpty()) {
            // Someone else's session reads as missing
            throw ResourceNotFoundException.of("Live session", id);
        }
        if (session.isCancelled()) {
            throw new BusinessRuleException("This session was cancelled");
        }
        if (!joinable(session, Instant.now())) {
            throw new BusinessRuleException(Instant.now().isBefore(session.getStartsAt())
                    ? "You can join " + properties.joinEarlyMinutes() + " minutes before the start"
                    : "This session has ended");
        }
        jdbcTemplate.update("""
                insert into live_attendance (session_id, student_id) values (?, ?) on conflict do nothing
                """, session.getId(), student.getId());
        return new LiveDtos.JoinResponse(session.getMeetingUrl());
    }

    @Override
    @Transactional
    public LiveDtos.CalendarFeed rotateCalendarToken(UUID userId) {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbcTemplate.update("""
                insert into calendar_tokens (user_id, token_hash) values (?, ?)
                on conflict (user_id) do update set token_hash = excluded.token_hash, created_at = now()
                """, userId, tokenHasher.hash(CALENDAR_PURPOSE, token));
        return new LiveDtos.CalendarFeed(properties.apiBaseUrl() + "/calendar/" + token + ".ics");
    }

    @Override
    @Transactional(readOnly = true)
    public String calendar(String token) {
        List<UUID> users = jdbcTemplate.queryForList("select user_id from calendar_tokens where token_hash = ?",
                UUID.class, tokenHasher.hash(CALENDAR_PURPOSE, token));
        if (users.isEmpty()) {
            return null;
        }
        Instant now = Instant.now();
        List<LiveSession> sessions = studentRepository.findByUserId(users.getFirst())
                .map(student -> sessionsFor(student.getId(), now.minus(PAST_SHOWN), now.plus(FEED_AHEAD)))
                .orElse(List.of());
        return ics(sessions, now);
    }

    @Override
    @Transactional
    public int sendReminders() {
        Instant now = Instant.now();
        int reminded = 0;
        for (LiveSession session : sessionRepository.findStartingForUpdate(now, now.plus(HOUR_REMINDER))) {
            boolean soon = !session.getStartsAt().isAfter(now.plus(SOON_REMINDER));
            if (soon && !session.isRemindedSoon()) {
                notifyEnrolled(session, "Live session starting soon", session.getTitle() + " starts in a few minutes.");
                session.setRemindedSoon(true);
                session.setRemindedHour(true);
                reminded++;
            } else if (!soon && !session.isRemindedHour()) {
                notifyEnrolled(session, "Live session in an hour", session.getTitle() + " starts at "
                        + readable(session.getStartsAt()) + " (UTC).");
                session.setRemindedHour(true);
                reminded++;
            }
        }
        return reminded;
    }

    private List<LiveSession> sessionsFor(Integer studentId, Instant from, Instant until) {
        List<Integer> courseIds = jdbcTemplate.queryForList(
                "select course_id from student_courses where student_id = ?", Integer.class, studentId);
        return courseIds.isEmpty() ? List.of() : sessionRepository.findInWindow(courseIds, from, until);
    }

    private Set<Integer> attendedSessions(Integer studentId) {
        return new HashSet<>(jdbcTemplate.queryForList(
                "select session_id from live_attendance where student_id = ?", Integer.class, studentId));
    }

    private boolean joinable(LiveSession session, Instant now) {
        return !session.isCancelled()
                && !now.isBefore(session.getStartsAt().minus(Duration.ofMinutes(properties.joinEarlyMinutes())))
                && now.isBefore(session.endsAt());
    }

    private void apply(LiveSession session, Course course, LiveDtos.SessionRequest request) {
        URI meeting;
        try {
            meeting = URI.create(request.meetingUrl().trim());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("Meeting link is not a valid URL");
        }
        if (!"https".equalsIgnoreCase(meeting.getScheme()) || meeting.getHost() == null) {
            throw new BusinessRuleException("Meeting link must be an https URL");
        }
        if (request.recordingLessonId() != null && !lessonRepository.findById(request.recordingLessonId())
                .map(lesson -> lesson.getSection().getCourse().getId().equals(course.getId()))
                .orElse(false)) {
            throw new BusinessRuleException("The recording must be a lesson in this course");
        }
        session.setTitle(request.title().trim());
        session.setDescription(StringUtils.hasText(request.description()) ? request.description().trim() : null);
        session.setStartsAt(request.startsAt());
        session.setDurationMinutes(request.durationMinutes());
        session.setMeetingUrl(meeting.toString());
        session.setRecordingLessonId(request.recordingLessonId());
    }

    private void notifyEnrolled(LiveSession session, String title, String message) {
        List<Integer> students = studentCourseRepository.findStudentIdsByCourseId(session.getCourse().getId());
        students.forEach(studentId -> notificationService.notifyStudent(studentId, NotificationType.LIVE_SESSION,
                title, message, "/learn/" + session.getCourse().getSlug() + "?live=" + session.getId()));
    }

    private LiveDtos.ManagedSession toManaged(LiveSession session) {
        Long attendees = jdbcTemplate.queryForObject("select count(*) from live_attendance where session_id = ?",
                Long.class, session.getId());
        return new LiveDtos.ManagedSession(session.getId(), session.getTitle(), session.getDescription(),
                session.getStartsAt(), session.endsAt(), session.getDurationMinutes(), session.getMeetingUrl(),
                session.getRecordingLessonId(), session.isCancelled(), attendees == null ? 0 : attendees);
    }

    /** RFC 5545 calendar. Events link to the app's join page, not the meeting, so joining is still recorded. */
    private String ics(List<LiveSession> sessions, Instant now) {
        StringBuilder out = new StringBuilder();
        line(out, "BEGIN:VCALENDAR");
        line(out, "VERSION:2.0");
        line(out, "PRODID:-//CourseAuraHub//Live sessions//EN");
        line(out, "CALSCALE:GREGORIAN");
        line(out, "METHOD:PUBLISH");
        line(out, "X-WR-CALNAME:" + escape(settingsService.getPublicSettings().platformName() + " live sessions"));
        for (LiveSession session : sessions) {
            String link = properties.siteUrl() + "/learn/" + session.getCourse().getSlug() + "?live=" + session.getId();
            line(out, "BEGIN:VEVENT");
            line(out, "UID:live-session-" + session.getId() + "@courseaurahub");
            line(out, "DTSTAMP:" + ICS_TIME.format(now));
            line(out, "DTSTART:" + ICS_TIME.format(session.getStartsAt()));
            line(out, "DTEND:" + ICS_TIME.format(session.endsAt()));
            line(out, "SUMMARY:" + escape(session.getTitle()));
            line(out, "DESCRIPTION:" + escape(session.getCourse().getTitle() + "\nJoin: " + link
                    + (session.getDescription() == null ? "" : "\n\n" + session.getDescription())));
            line(out, "URL:" + link);
            line(out, "STATUS:" + (session.isCancelled() ? "CANCELLED" : "CONFIRMED"));
            line(out, "END:VEVENT");
        }
        line(out, "END:VCALENDAR");
        return out.toString();
    }

    /** Folds lines longer than 75 characters, as calendar apps expect. */
    private static void line(StringBuilder out, String text) {
        int start = 0;
        while (text.length() - start > 75) {
            out.append(text, start, start + 75).append("\r\n ");
            start += 75;
        }
        out.append(text, start, text.length()).append("\r\n");
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace(";", "\;").replace(",", "\\,").replace("\r", "").replace("\n", "\\n");
    }

    private static String readable(Instant instant) {
        return DateTimeFormatter.ofPattern("EEE d MMM yyyy, HH:mm").withZone(ZoneOffset.UTC).format(instant);
    }

    private Course managed(Actor actor, String slug) {
        Course course = courseAccess.requireCourse(slug);
        courseAccess.requireManager(course, actor);
        return course;
    }

    private LiveSession owned(Course course, Integer id) {
        LiveSession session = sessionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Live session", id));
        if (!session.getCourse().getId().equals(course.getId())) {
            throw ResourceNotFoundException.of("Live session", id);
        }
        return session;
    }
}
