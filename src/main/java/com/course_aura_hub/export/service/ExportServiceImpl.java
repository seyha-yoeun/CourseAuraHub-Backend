package com.course_aura_hub.export.service;

import com.course_aura_hub.common.exception.BusinessRuleException;
import com.course_aura_hub.common.security.Actor;
import com.course_aura_hub.course.model.Course;
import com.course_aura_hub.course.service.CourseAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.Writer;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Set;

/**
 * Streams rows straight from the database to the response, one row at a time, so a large export never sits in
 * memory. Date filters are whole UTC days, both ends inclusive.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExportServiceImpl implements ExportService {

    private static final int FETCH_SIZE = 500;
    private static final Set<String> ORDER_STATUSES = Set.of("PENDING", "PAID", "FAILED", "CANCELLED", "REFUNDED");
    private static final Timestamp EARLIEST = Timestamp.valueOf("1970-01-01 00:00:00");
    private static final Timestamp LATEST = Timestamp.valueOf("9999-12-31 00:00:00");

    private final JdbcTemplate jdbcTemplate;
    private final CourseAccess courseAccess;

    @Override
    public void validateOrderStatus(String status) {
        if (StringUtils.hasText(status) && !ORDER_STATUSES.contains(status.trim().toUpperCase(Locale.ROOT))) {
            throw new BusinessRuleException("Unknown order status: " + status);
        }
    }

    @Override
    public void requireCourseManager(Actor actor, String slug) {
        managed(actor, slug);
    }

    @Override
    public void orders(Writer out, LocalDate from, LocalDate to, String status) {
        validateOrderStatus(status);
        String filter = StringUtils.hasText(status) ? status.trim().toUpperCase(Locale.ROOT) : null;
        CsvWriter csv = new CsvWriter(out).row("order_number", "created_at", "paid_at", "refunded_at", "status", "provider",
                "course_slug", "course_title", "learner_name", "learner_email", "list_amount", "discount_amount", "amount",
                "currency", "coupon_code");
        stream("""
                select o.order_number, o.created_at, o.paid_at, o.refunded_at, o.status, o.provider, c.slug, c.title,
                       s.name, s.email, o.list_amount, o.discount_amount, o.amount, o.currency, cp.code
                from orders o
                join courses c on c.id = o.course_id
                join students s on s.id = o.student_id
                left join coupons cp on cp.id = o.coupon_id
                where o.created_at >= ? and o.created_at < ? and (cast(? as text) is null or o.status = ?)
                order by o.created_at, o.id
                """, csv, 15, start(from), end(to), filter, filter);
    }

    @Override
    public void enrollments(Writer out, LocalDate from, LocalDate to) {
        CsvWriter csv = new CsvWriter(out).row("enrollment_id", "enrolled_at", "course_slug", "course_title", "learner_name",
                "learner_email", "amount");
        stream("""
                select e.id, e.enrolled_at, c.slug, c.title, s.name, s.email, e.amount
                from enrollments e
                join courses c on c.id = e.course_id
                join students s on s.id = e.student_id
                where e.enrolled_at >= ? and e.enrolled_at <= ?
                order by e.enrolled_at, e.id
                """, csv, 7, from == null ? LocalDate.of(1970, 1, 1) : from, to == null ? LocalDate.of(9999, 12, 31) : to);
    }

    @Override
    public void learners(Writer out) {
        CsvWriter csv = new CsvWriter(out).row("student_id", "name", "email", "country", "status", "joined_at", "last_active",
                "total_spent", "courses", "completed_courses", "has_account");
        stream("""
                select s.id, s.name, s.email, s.country, s.status, s.joined_at, s.last_active, s.total_spent,
                       (select count(*) from student_courses sc where sc.student_id = s.id),
                       (select count(*) from student_courses sc where sc.student_id = s.id and sc.completed),
                       s.user_id is not null
                from students s
                order by s.id
                """, csv, 11);
    }

    @Override
    public void memberCardLedger(Writer out, LocalDate from, LocalDate to) {
        CsvWriter csv = new CsvWriter(out).row("created_at", "card_number", "holder_name", "type", "amount", "balance_after",
                "reference", "note");
        stream("""
                select t.created_at, c.card_number, c.holder_name, t.type, t.amount, t.balance_after, t.reference, t.note
                from member_card_transactions t
                join member_cards c on c.id = t.card_id
                where t.created_at >= ? and t.created_at < ?
                order by t.created_at, t.id
                """, csv, 8, start(from), end(to));
    }

    @Override
    public void auditLog(Writer out, LocalDate from, LocalDate to) {
        CsvWriter csv = new CsvWriter(out).row("created_at", "actor_email", "actor_role", "action", "http_method", "path",
                "resource", "resource_id", "status", "outcome", "request_id", "ip_address");
        stream("""
                select created_at, actor_email, actor_role, action, http_method, path_pattern, resource, resource_id,
                       status, outcome, request_id, ip_address
                from admin_audit_events
                where created_at >= ? and created_at < ?
                order by created_at, id
                """, csv, 12, start(from), end(to));
    }

    @Override
    public void courseLearners(Writer out, Actor actor, String slug) {
        Course course = managed(actor, slug);
        CsvWriter csv = new CsvWriter(out).row("learner_name", "enrolled_at", "completed_lessons", "total_lessons",
                "progress_percent", "completed", "certificate_number");
        stream("""
                select s.name, e.enrolled_at,
                       (select count(*) from lesson_progress lp join lessons l on l.id = lp.lesson_id
                        join course_sections cs on cs.id = l.section_id
                        where lp.student_id = s.id and cs.course_id = sc.course_id) as done,
                       t.total,
                       case when t.total = 0 then 0 else round(100.0 * (select count(*) from lesson_progress lp
                            join lessons l on l.id = lp.lesson_id join course_sections cs on cs.id = l.section_id
                            where lp.student_id = s.id and cs.course_id = sc.course_id) / t.total) end,
                       sc.completed,
                       cc.certificate_number
                from student_courses sc
                join students s on s.id = sc.student_id
                left join enrollments e on e.student_id = sc.student_id and e.course_id = sc.course_id
                left join course_certificates cc on cc.student_id = sc.student_id and cc.course_id = sc.course_id
                cross join (select count(*) as total from lessons l join course_sections cs on cs.id = l.section_id
                            where cs.course_id = ?) t
                where sc.course_id = ?
                order by s.name, s.id
                """, csv, 7, course.getId(), course.getId());
    }

    @Override
    public void courseGrades(Writer out, Actor actor, String slug) {
        Course course = managed(actor, slug);
        CsvWriter csv = new CsvWriter(out).row("learner_name", "lesson", "status", "score", "pass_score", "submitted_at",
                "graded_at");
        stream("""
                select s.name, l.title, sub.status, sub.score, a.pass_score, sub.submitted_at, sub.graded_at
                from assignment_submissions sub
                join assignments a on a.id = sub.assignment_id
                join lessons l on l.id = a.lesson_id
                join course_sections cs on cs.id = l.section_id
                join students s on s.id = sub.student_id
                where cs.course_id = ?
                order by l.title, s.name, sub.id
                """, csv, 7, course.getId());
    }

    private Course managed(Actor actor, String slug) {
        Course course = courseAccess.requireCourse(slug);
        courseAccess.requireManager(course, actor);
        return course;
    }

    private void stream(String sql, CsvWriter csv, int columns, Object... args) {
        jdbcTemplate.query(connection -> {
            var statement = connection.prepareStatement(sql);
            statement.setFetchSize(FETCH_SIZE);
            for (int i = 0; i < args.length; i++) {
                statement.setObject(i + 1, args[i]);
            }
            return statement;
        }, rs -> {
            Object[] cells = new Object[columns];
            for (int i = 0; i < columns; i++) {
                cells[i] = rs.getObject(i + 1);
            }
            csv.row(cells);
        });
    }

    private static Timestamp start(LocalDate from) {
        return from == null ? EARLIEST : Timestamp.from(from.atStartOfDay().toInstant(ZoneOffset.UTC));
    }

    private static Timestamp end(LocalDate to) {
        return to == null ? LATEST : Timestamp.from(to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC));
    }
}
