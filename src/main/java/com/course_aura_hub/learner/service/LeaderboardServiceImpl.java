package com.course_aura_hub.learner.service;

import com.course_aura_hub.common.exception.BusinessRuleException;
import com.course_aura_hub.common.exception.ResourceNotFoundException;
import com.course_aura_hub.common.util.DisplayNames;
import com.course_aura_hub.course.model.Course;
import com.course_aura_hub.course.model.CourseStatus;
import com.course_aura_hub.course.repository.CourseRepository;
import com.course_aura_hub.learner.dto.LeaderboardDtos;
import com.course_aura_hub.learner.model.LearnerGoal;
import com.course_aura_hub.learner.repository.LearnerGoalRepository;
import com.course_aura_hub.student.model.Student;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class LeaderboardServiceImpl implements LeaderboardService {

    private static final int MAX_LIMIT = 50;
    private static final Duration CACHE_TTL = Duration.ofSeconds(60);
    private static final int ALL_COURSES = -1;

    private static final String RANKING = """
            with xp as (
                select lp.student_id, 10 * count(*) as xp, count(*) as lessons
                from lesson_progress lp
                join lessons l on l.id = lp.lesson_id
                join course_sections cs on cs.id = l.section_id
                where lp.completed_at >= :from and (:courseId = -1 or cs.course_id = :courseId)
                group by lp.student_id
                union all
                select q.student_id, 20 * count(distinct q.lesson_id), 0
                from quiz_attempts q
                join lessons l on l.id = q.lesson_id
                join course_sections cs on cs.id = l.section_id
                where q.passed and q.submitted_at >= :from and (:courseId = -1 or cs.course_id = :courseId)
                group by q.student_id
                union all
                select c.student_id, 100 * count(*), 0
                from course_certificates c
                where c.issued_at >= :from and (:courseId = -1 or c.course_id = :courseId)
                group by c.student_id
            )
            select s.id, s.name, s.avatar, sum(x.xp) as xp, sum(x.lessons) as lessons
            from xp x
            join students s on s.id = x.student_id
            left join users u on u.id = s.user_id
            left join learner_goals g on g.student_id = s.id
            where coalesce(g.leaderboard_visible, true)
              and (u.id is null or u.enabled)
              and coalesce(s.email, '') not like 'deleted-student-%'
            group by s.id, s.name, s.avatar
            order by xp desc, lessons desc, s.id
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final LearnerAccountService learnerAccountService;
    private final LearnerGoalRepository goalRepository;
    private final CourseRepository courseRepository;

    /** Rankings change slowly, and every viewer of a board sees the same one, so each is kept for a minute. */
    private final Map<String, CachedRanking> cache = new ConcurrentHashMap<>();

    private record Row(Integer studentId, String name, String avatar, long xp, long lessons) {
    }

    private record CachedRanking(Instant expiresAt, List<Row> rows) {
    }

    @Override
    @Transactional(readOnly = true)
    public LeaderboardDtos.Leaderboard leaderboard(String email, String scope, String courseSlug, int limit) {
        String normalizedScope = StringUtils.hasText(scope) ? scope.trim().toLowerCase(Locale.ROOT) : "week";
        Instant from = startOf(normalizedScope);
        Integer courseId = ALL_COURSES;
        if (StringUtils.hasText(courseSlug)) {
            courseId = courseRepository.findBySlug(courseSlug.trim())
                    .filter(course -> course.getStatus() == CourseStatus.PUBLISHED)
                    .map(Course::getId)
                    .orElseThrow(() -> ResourceNotFoundException.of("Course", courseSlug));
        }

        List<Row> rows = ranking(normalizedScope, courseId, from);
        Integer myId = learnerAccountService.findCurrentStudent(email).map(Student::getId).orElse(null);
        boolean visible = myId == null || goalRepository.findById(myId).map(LearnerGoal::isLeaderboardVisible).orElse(true);

        int size = Math.max(1, Math.min(limit, MAX_LIMIT));
        List<LeaderboardDtos.Entry> entries = new ArrayList<>();
        LeaderboardDtos.Entry me = null;
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            boolean you = row.studentId().equals(myId);
            if (i < size || you) {
                LeaderboardDtos.Entry entry = new LeaderboardDtos.Entry(
                        i + 1, DisplayNames.shortName(row.name()), row.avatar(), row.xp(), row.lessons(), you);
                if (i < size) {
                    entries.add(entry);
                }
                if (you) {
                    me = entry;
                }
            }
        }
        return new LeaderboardDtos.Leaderboard(normalizedScope,
                courseId == ALL_COURSES ? null : courseSlug.trim(), from, entries, me, visible);
    }

    @Override
    @Transactional(readOnly = true)
    public LeaderboardDtos.GoalResponse goal(String email, String timeZone) {
        ZoneId zone = resolveZone(timeZone);
        Optional<Student> student = learnerAccountService.findCurrentStudent(email);
        LearnerGoal goal = student.flatMap(found -> goalRepository.findById(found.getId())).orElse(null);
        return toGoalResponse(student.map(Student::getId).orElse(null), goal, zone);
    }

    @Override
    @Transactional
    public LeaderboardDtos.GoalResponse updateGoal(String email, LeaderboardDtos.GoalRequest request, String timeZone) {
        ZoneId zone = resolveZone(timeZone);
        Integer studentId = learnerAccountService.currentStudent(email).getId();
        LearnerGoal goal = goalRepository.findById(studentId)
                .orElseGet(() -> LearnerGoal.builder().studentId(studentId).build());
        goal.setWeeklyLessonsTarget(request.weeklyLessonsTarget());
        if (request.leaderboardVisible() != null) {
            goal.setLeaderboardVisible(request.leaderboardVisible());
            // Hiding should take effect at once, not after the cached boards expire
            cache.clear();
        }
        return toGoalResponse(studentId, goalRepository.save(goal), zone);
    }

    private List<Row> ranking(String scope, Integer courseId, Instant from) {
        String key = scope + "|" + courseId + "|" + from;
        Instant now = Instant.now();
        CachedRanking cached = cache.get(key);
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.rows();
        }
        cache.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("from", Timestamp.from(from))
                .addValue("courseId", courseId);
        List<Row> rows = jdbc.query(RANKING, params, (rs, i) -> new Row(
                rs.getInt("id"), rs.getString("name"), rs.getString("avatar"), rs.getLong("xp"), rs.getLong("lessons")));
        cache.put(key, new CachedRanking(now.plus(CACHE_TTL), rows));
        return rows;
    }

    private LeaderboardDtos.GoalResponse toGoalResponse(Integer studentId, LearnerGoal goal, ZoneId zone) {
        Instant weekStart = LocalDate.now(zone).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay(zone).toInstant();
        long lessons = 0;
        if (studentId != null) {
            Long count = jdbc.queryForObject(
                    "select count(*) from lesson_progress where student_id = :studentId and completed_at >= :from",
                    new MapSqlParameterSource().addValue("studentId", studentId).addValue("from", Timestamp.from(weekStart)),
                    Long.class);
            lessons = count == null ? 0 : count;
        }
        Integer target = goal == null ? null : goal.getWeeklyLessonsTarget();
        int percent = target == null ? 0 : (int) Math.min(100, lessons * 100 / target);
        return new LeaderboardDtos.GoalResponse(target, lessons, percent, target != null && lessons >= target,
                weekStart, goal == null || goal.isLeaderboardVisible());
    }

    private static Instant startOf(String scope) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate start = switch (scope) {
            case "week" -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case "month" -> today.withDayOfMonth(1);
            default -> throw new BusinessRuleException("scope must be week or month");
        };
        return start.atStartOfDay(ZoneOffset.UTC).toInstant();
    }


    private static ZoneId resolveZone(String timeZone) {
        if (!StringUtils.hasText(timeZone)) {
            return ZoneOffset.UTC;
        }
        try {
            return ZoneId.of(timeZone.trim());
        } catch (DateTimeException ex) {
            throw new BusinessRuleException("Unknown time zone: " + timeZone);
        }
    }
}
