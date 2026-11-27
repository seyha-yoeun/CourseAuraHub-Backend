package com.course_aura_hub.chat.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Who belongs to a course for chat, decided live on every call.
 *
 * <p>An <b>eligible member</b> of a course is an enabled account that is enrolled in it, or is its staff: the
 * course owner, or any admin when the course has no owner. Platform admins are not members of courses that have
 * an owner. Refunds, expiry and unenrollment remove enrollments without an event, so nothing here is cached.</p>
 */
@Component
@RequiredArgsConstructor
public class ChatEligibility {

    /** True when user {@code u} (a users row) is staff of course {@code c} (a courses row). */
    static final String STAFF_SQL = "(c.owner_id = u.id or (c.owner_id is null and u.role = 'ROLE_ADMIN'))";

    /** True when user {@code u} is an eligible member of course {@code c}. */
    static final String ELIGIBLE_SQL = "(u.enabled and (" + STAFF_SQL + " or exists ("
            + "select 1 from student_courses sc join students s on s.id = sc.student_id "
            + "where sc.course_id = c.id and s.user_id = u.id)))";

    private final NamedParameterJdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public boolean isEligible(UUID userId, Integer courseId) {
        if (userId == null || courseId == null) {
            return false;
        }
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists (select 1 from users u, courses c where u.id = :userId and c.id = :courseId and "
                        + ELIGIBLE_SQL + ")",
                new MapSqlParameterSource("userId", userId).addValue("courseId", courseId), Boolean.class));
    }

    /** Course staff: the owner, or an admin for an owner-less course. */
    @Transactional(readOnly = true)
    public boolean isStaff(UUID userId, Integer courseId) {
        if (userId == null || courseId == null) {
            return false;
        }
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists (select 1 from users u, courses c where u.id = :userId and c.id = :courseId and u.enabled and "
                        + STAFF_SQL + ")",
                new MapSqlParameterSource("userId", userId).addValue("courseId", courseId), Boolean.class));
    }

    /** The given users who are eligible members of the course, in one query. */
    @Transactional(readOnly = true)
    public Set<UUID> eligibleAmong(Integer courseId, Collection<UUID> userIds) {
        if (courseId == null || userIds.isEmpty()) {
            return Set.of();
        }
        List<UUID> ids = jdbc.queryForList(
                "select u.id from users u, courses c where c.id = :courseId and u.id in (:userIds) and " + ELIGIBLE_SQL,
                new MapSqlParameterSource("courseId", courseId).addValue("userIds", userIds), UUID.class);
        return new HashSet<>(ids);
    }

    /** Courses in which both people are eligible members. */
    @Transactional(readOnly = true)
    public List<Integer> sharedCourseIds(UUID a, UUID b) {
        return jdbc.queryForList("""
                select c.id from courses c
                where exists (select 1 from users u where u.id = :a and %s)
                  and exists (select 1 from users u where u.id = :b and %s)
                """.formatted(ELIGIBLE_SQL, ELIGIBLE_SQL),
                new MapSqlParameterSource("a", a).addValue("b", b), Integer.class);
    }

    /** True when {@code staffId} is staff of some course in which {@code otherId} is an eligible member. */
    @Transactional(readOnly = true)
    public boolean isStaffOfSharedCourse(UUID staffId, UUID otherId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists (
                    select 1 from courses c
                    where exists (select 1 from users u where u.id = :staff and u.enabled and %s)
                      and exists (select 1 from users u where u.id = :other and %s))
                """.formatted(STAFF_SQL, ELIGIBLE_SQL),
                new MapSqlParameterSource("staff", staffId).addValue("other", otherId), Boolean.class));
    }
}
