package com.course_aura_hub.privacy.service;

import com.course_aura_hub.auth.model.AuditEventType;
import com.course_aura_hub.auth.model.Role;
import com.course_aura_hub.auth.model.SessionRevocationReason;
import com.course_aura_hub.auth.model.User;
import com.course_aura_hub.auth.repository.UserRepository;
import com.course_aura_hub.auth.service.AuthAuditEntry;
import com.course_aura_hub.auth.service.AuthAuditService;
import com.course_aura_hub.auth.service.RefreshSessionService;
import com.course_aura_hub.common.exception.BusinessRuleException;
import com.course_aura_hub.common.exception.ForbiddenActionException;
import com.course_aura_hub.common.exception.ResourceNotFoundException;
import com.course_aura_hub.privacy.dto.DataExportResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountPrivacyServiceImpl implements AccountPrivacyService {

    static final String DELETED_NAME = "Deleted learner";

    /** Learner data, keyed by the name it gets in the export. Each query takes :studentId or :userId. */
    private static final Map<String, String> EXPORT_QUERIES = exportQueries();

    private final UserRepository userRepository;
    private final RefreshSessionService refreshSessionService;
    private final AuthAuditService auditService;
    private final PasswordEncoder passwordEncoder;
    private final NamedParameterJdbcTemplate jdbc;

    @Override
    @Transactional(readOnly = true)
    public DataExportResponse export(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> ResourceNotFoundException.of("User", userId));
        Map<String, Object> account = new LinkedHashMap<>();
        account.put("id", user.getId());
        account.put("email", user.getEmail());
        account.put("firstName", user.getFirstName());
        account.put("lastName", user.getLastName());
        account.put("role", user.getRole().name());
        account.put("provider", user.getProvider().name());
        account.put("imageUrl", user.getImageUrl());
        account.put("bio", user.getBio());
        account.put("website", user.getWebsite());
        account.put("twitter", user.getTwitter());
        account.put("emailVerified", user.isEmailVerified());
        account.put("mfaEnabled", user.isMfaEnabled());
        account.put("createdAt", user.getCreatedAt());

        Integer studentId = findStudentId(userId);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("studentId", studentId == null ? -1 : studentId);
        Map<String, Object> learner = studentId == null ? Map.of() : jdbc.queryForMap("""
                select name, email, avatar, total_spent as "totalSpent", joined_at as "joinedAt",
                       last_active as "lastActive", status, country, reminders_enabled as "remindersEnabled"
                from students where id = :studentId
                """, params);

        Map<String, List<Map<String, Object>>> records = new LinkedHashMap<>();
        EXPORT_QUERIES.forEach((name, sql) -> records.put(name, jdbc.queryForList(sql, params)));
        log.info("Exported personal data for user {}", userId);
        return new DataExportResponse(Instant.now(), account, learner, records);
    }

    @Override
    @Transactional
    public void deleteAccount(UUID userId, String password, String confirmation) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("User", userId));
        if (!user.getEmail().equalsIgnoreCase(confirmation == null ? "" : confirmation.trim())) {
            throw new BusinessRuleException("Type your account email to confirm");
        }
        if (StringUtils.hasText(user.getPassword())
                && (password == null || !passwordEncoder.matches(password, user.getPassword()))) {
            auditService.record(AuthAuditEntry.failure(AuditEventType.ACCOUNT_DELETED, user, "WRONG_PASSWORD").build());
            throw new BusinessRuleException("Password is incorrect");
        }
        if (user.getRole() == Role.ROLE_ADMIN) {
            throw new ForbiddenActionException("Admins can't delete their own account. Ask another admin to change your role first.");
        }

        Integer studentId = findStudentId(userId);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("studentId", studentId == null ? -1 : studentId);
        BigDecimal balance = jdbc.queryForObject(
                "select coalesce(sum(balance), 0) from member_cards where student_id = :studentId",
                params, BigDecimal.class);
        if (balance != null && balance.signum() > 0) {
            throw new BusinessRuleException("Your member card still holds $" + balance
                    + ". Spend it or ask support to close the card before deleting your account.");
        }

        // Sign-in methods and secrets
        for (String table : List.of("user_oauth_identities", "user_totp_credentials", "mfa_recovery_codes",
                "auth_one_time_tokens", "telegram_links", "notifications")) {
            jdbc.update("delete from " + table + " where user_id = :userId", params);
        }
        // Learner extras that are not records of money or achievement
        for (String table : List.of("wishlist_items", "cart_items", "lesson_notes", "learner_reminders")) {
            jdbc.update("delete from " + table + " where student_id = :studentId", params);
        }
        jdbc.update("update orders set status = 'CANCELLED', checkout_url = null "
                + "where student_id = :studentId and status = 'PENDING'", params);
        jdbc.update("update lesson_questions set author_id = null, author_name = :name where author_id = :userId",
                params.addValue("name", DELETED_NAME));
        jdbc.update("update lesson_answers set author_id = null, author_name = :name where author_id = :userId", params);
        jdbc.update("update course_certificates set learner_name = :name where student_id = :studentId", params);
        jdbc.update("update member_cards set holder_name = :name where student_id = :studentId", params);
        if (studentId != null) {
            jdbc.update("""
                    update students set name = :name, email = :studentEmail, avatar = '', country = '',
                           status = 'INACTIVE', reminders_enabled = false
                    where id = :studentId
                    """, params.addValue("studentEmail", "deleted-student-" + studentId + "@deleted.invalid"));
        }

        refreshSessionService.revokeAllSessions(userId, SessionRevocationReason.ACCOUNT_DELETED);
        user.setEmail(("deleted-" + userId + "@deleted.invalid").toLowerCase(Locale.ROOT));
        user.setFirstName("Deleted");
        user.setLastName("user");
        user.setPassword(null);
        user.setProviderId(null);
        user.setImageUrl(null);
        user.setBio(null);
        user.setWebsite(null);
        user.setTwitter(null);
        user.setAccessToken(null);
        user.setRefreshToken(null);
        user.setRefreshTokenExpiresAt(null);
        user.setMfaEnabled(false);
        user.setEnabled(false);
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);

        // The audit row keeps the user id only, so the old address is not kept here either
        auditService.record(AuthAuditEntry.builder()
                .type(AuditEventType.ACCOUNT_DELETED)
                .outcome(com.course_aura_hub.auth.model.AuditOutcome.SUCCESS)
                .userId(userId)
                .build());
        log.info("Deleted account {}", userId);
    }

    private Integer findStudentId(UUID userId) {
        List<Integer> ids = jdbc.queryForList("select id from students where user_id = :userId",
                Map.of("userId", userId), Integer.class);
        return ids.isEmpty() ? null : ids.getFirst();
    }

    private static Map<String, String> exportQueries() {
        Map<String, String> queries = new LinkedHashMap<>();
        queries.put("courses", """
                select c.slug, c.title, sc.completed from student_courses sc
                join courses c on c.id = sc.course_id where sc.student_id = :studentId order by c.title""");
        queries.put("enrollments", """
                select c.slug, e.enrolled_at as "enrolledAt", e.amount from enrollments e
                join courses c on c.id = e.course_id where e.student_id = :studentId order by e.enrolled_at""");
        queries.put("orders", """
                select o.order_number as "orderNumber", c.slug, o.list_amount as "listAmount",
                       o.discount_amount as "discountAmount", o.amount, o.currency, o.status, o.provider,
                       o.created_at as "createdAt", o.paid_at as "paidAt", o.refunded_at as "refundedAt"
                from orders o join courses c on c.id = o.course_id where o.student_id = :studentId order by o.created_at""");
        queries.put("certificates", """
                select certificate_number as "certificateNumber", course_title as "courseTitle", issued_at as "issuedAt"
                from course_certificates where student_id = :studentId order by issued_at""");
        queries.put("lessonProgress", """
                select l.title as lesson, lp.completed_at as "completedAt" from lesson_progress lp
                join lessons l on l.id = lp.lesson_id where lp.student_id = :studentId order by lp.completed_at""");
        queries.put("quizAttempts", """
                select l.title as lesson, qa.score_percent as "scorePercent", qa.passed, qa.submitted_at as "submittedAt"
                from quiz_attempts qa join lessons l on l.id = qa.lesson_id where qa.student_id = :studentId""");
        queries.put("assignmentSubmissions", """
                select s.file_name as "fileName", s.comment, s.status, s.score, s.feedback, s.submitted_at as "submittedAt"
                from assignment_submissions s where s.student_id = :studentId""");
        queries.put("reviews", """
                select c.slug, r.rating, r.comment, r.created_at as "createdAt" from course_reviews r
                join courses c on c.id = r.course_id where r.student_id = :studentId""");
        queries.put("notes", """
                select l.title as lesson, n.body, n.position_seconds as "positionSeconds", n.created_at as "createdAt"
                from lesson_notes n join lessons l on l.id = n.lesson_id where n.student_id = :studentId""");
        queries.put("questions", """
                select q.body, q.resolved, q.created_at as "createdAt" from lesson_questions q where q.author_id = :userId""");
        queries.put("answers", """
                select a.body, a.created_at as "createdAt" from lesson_answers a where a.author_id = :userId""");
        queries.put("wishlist", """
                select c.slug, w.created_at as "createdAt" from wishlist_items w
                join courses c on c.id = w.course_id where w.student_id = :studentId""");
        queries.put("memberCardTransactions", """
                select t.type, t.amount, t.created_at as "createdAt" from member_card_transactions t
                join member_cards m on m.id = t.card_id where m.student_id = :studentId order by t.created_at""");
        queries.put("signInProviders", """
                select provider, email, created_at as "linkedAt", last_login_at as "lastLoginAt"
                from user_oauth_identities where user_id = :userId""");
        return queries;
    }
}
