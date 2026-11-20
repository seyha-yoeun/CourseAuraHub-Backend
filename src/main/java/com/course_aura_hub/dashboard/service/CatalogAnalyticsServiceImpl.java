package com.course_aura_hub.dashboard.service;

import com.course_aura_hub.auth.config.RateLimitProperties;
import com.course_aura_hub.common.exception.BusinessRuleException;
import com.course_aura_hub.common.exception.ResourceNotFoundException;
import com.course_aura_hub.course.model.CourseStatus;
import com.course_aura_hub.course.repository.CourseRepository;
import com.course_aura_hub.dashboard.config.CatalogAnalyticsProperties;
import com.course_aura_hub.dashboard.dto.FunnelDtos;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
public class CatalogAnalyticsServiceImpl implements CatalogAnalyticsService {

    private static final Set<String> EVENTS = Set.of("VIEW", "ADD_TO_CART", "CHECKOUT_START");
    private static final Pattern VISITOR_ID = Pattern.compile("[A-Za-z0-9-]{8,64}");
    private static final int MAX_TRACKED_CLIENTS = 10_000;
    private static final int MAX_RANGE_DAYS = 366;
    private static final int MAX_MONTHS = 24;
    private static final int ALL_COURSES = -1;

    private final NamedParameterJdbcTemplate jdbc;
    private final CourseRepository courseRepository;
    private final CatalogAnalyticsProperties properties;
    private final RateLimitProperties rateLimitProperties;
    private final Map<String, Bucket> buckets = new LinkedHashMap<>(1024, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Bucket> eldest) {
            return size() > MAX_TRACKED_CLIENTS;
        }
    };

    public CatalogAnalyticsServiceImpl(NamedParameterJdbcTemplate jdbc, CourseRepository courseRepository,
                                       CatalogAnalyticsProperties properties, RateLimitProperties rateLimitProperties) {
        this.jdbc = jdbc;
        this.courseRepository = courseRepository;
        this.properties = properties;
        this.rateLimitProperties = rateLimitProperties;
    }

    @Override
    @Transactional
    public boolean record(FunnelDtos.EventRequest request, UUID userId, String clientIp, String userAgent) {
        if (!properties.enabled()) {
            return true;
        }
        if (!allow(clientIp)) {
            return false;
        }
        String event = request.event().trim().toUpperCase(Locale.ROOT);
        if (!EVENTS.contains(event)) {
            throw new BusinessRuleException("event must be view, add_to_cart or checkout_start");
        }
        Integer courseId = courseRepository.findBySlug(request.courseSlug().trim())
                .filter(course -> course.getStatus() == CourseStatus.PUBLISHED)
                .map(course -> course.getId())
                .orElseThrow(() -> ResourceNotFoundException.of("Course", request.courseSlug()));

        String visitor = visitorHash(userId, request.visitorId(), clientIp, userAgent);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("courseId", courseId)
                .addValue("event", event)
                .addValue("visitor", visitor)
                .addValue("since", Timestamp.from(Instant.now().minus(properties.dedupeWindow())));
        jdbc.update("""
                insert into catalog_events (course_id, event, visitor_hash)
                select :courseId, :event, :visitor
                where not exists (select 1 from catalog_events
                                  where visitor_hash = :visitor and course_id = :courseId and event = :event
                                    and created_at >= :since)
                """, params);
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public FunnelDtos.Funnel funnel(LocalDate from, LocalDate to, String courseSlug) {
        LocalDate end = to == null ? LocalDate.now(ZoneOffset.UTC) : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        if (start.isAfter(end)) {
            throw new BusinessRuleException("from must not be after to");
        }
        if (ChronoUnit.DAYS.between(start, end) >= MAX_RANGE_DAYS) {
            throw new BusinessRuleException("Choose a range of at most a year");
        }
        int courseId = ALL_COURSES;
        String slug = null;
        if (StringUtils.hasText(courseSlug)) {
            slug = courseSlug.trim();
            courseId = courseRepository.findBySlug(slug).map(course -> course.getId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Course", courseSlug));
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("courseId", courseId)
                .addValue("start", Timestamp.from(start.atStartOfDay(ZoneOffset.UTC).toInstant()))
                .addValue("end", Timestamp.from(end.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()))
                .addValue("startDate", Date.valueOf(start))
                .addValue("endDate", Date.valueOf(end));

        Map<String, Long> visitors = new HashMap<>();
        jdbc.query("""
                select event, count(distinct visitor_hash) as visitors from catalog_events
                where created_at >= :start and created_at < :end and (:courseId = -1 or course_id = :courseId)
                group by event
                """, params, rs -> {
            visitors.put(rs.getString("event"), rs.getLong("visitors"));
        });

        // Learners who enrolled in the range, and how far they have got since
        Map<String, Object> learners = jdbc.queryForMap("""
                with enrolled as (
                    select e.student_id, e.course_id from enrollments e
                    where e.enrolled_at between :startDate and :endDate and (:courseId = -1 or e.course_id = :courseId)
                ),
                progress as (
                    select en.student_id, en.course_id,
                           (select count(*) from lesson_progress lp join lessons l on l.id = lp.lesson_id
                            join course_sections cs on cs.id = l.section_id
                            where lp.student_id = en.student_id and cs.course_id = en.course_id) as done,
                           (select count(*) from lessons l join course_sections cs on cs.id = l.section_id
                            where cs.course_id = en.course_id) as total,
                           coalesce((select sc.completed from student_courses sc
                                     where sc.student_id = en.student_id and sc.course_id = en.course_id), false) as completed
                    from enrolled en
                )
                select count(*) as enrolled,
                       count(*) filter (where total > 0 and done * 2 >= total) as halfway,
                       count(*) filter (where completed) as completed
                from progress
                """, params);

        long[] counts = {
                visitors.getOrDefault("VIEW", 0L),
                visitors.getOrDefault("ADD_TO_CART", 0L),
                visitors.getOrDefault("CHECKOUT_START", 0L),
                ((Number) learners.get("enrolled")).longValue(),
                ((Number) learners.get("halfway")).longValue(),
                ((Number) learners.get("completed")).longValue()};
        String[][] names = {
                {"view", "Viewed a course page"},
                {"add_to_cart", "Added to cart"},
                {"checkout_start", "Started checkout"},
                {"enrolled", "Enrolled"},
                {"halfway", "Reached half way"},
                {"completed", "Completed"}};

        List<FunnelDtos.Step> steps = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            BigDecimal fromPrevious = i == 0 ? null : percent(counts[i], counts[i - 1]);
            steps.add(new FunnelDtos.Step(names[i][0], names[i][1], counts[i], fromPrevious, percent(counts[i], counts[0])));
        }
        return new FunnelDtos.Funnel(start, end, slug, steps);
    }

    @Override
    @Transactional(readOnly = true)
    public FunnelDtos.Retention retention(int months) {
        int span = Math.max(1, Math.min(months, MAX_MONTHS));
        YearMonth current = YearMonth.now(ZoneOffset.UTC);
        YearMonth first = current.minusMonths(span - 1L);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("startDate", Date.valueOf(first.atDay(1)))
                .addValue("startTs", Timestamp.from(first.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant()));

        Map<YearMonth, Long> sizes = new LinkedHashMap<>();
        jdbc.query("""
                select to_char(s.joined_at, 'YYYY-MM') as cohort, count(*) as learners from students s
                where s.joined_at >= :startDate and coalesce(s.email, '') not like 'deleted-student-%'
                group by 1
                """, params, rs -> {
            sizes.put(YearMonth.parse(rs.getString("cohort")), rs.getLong("learners"));
        });
        Map<String, Long> active = new HashMap<>();
        jdbc.query("""
                select to_char(s.joined_at, 'YYYY-MM') as cohort,
                       to_char(lp.completed_at at time zone 'UTC', 'YYYY-MM') as month,
                       count(distinct s.id) as active
                from students s
                join lesson_progress lp on lp.student_id = s.id
                where s.joined_at >= :startDate and lp.completed_at >= :startTs
                  and coalesce(s.email, '') not like 'deleted-student-%'
                group by 1, 2
                """, params, rs -> {
            active.put(rs.getString("cohort") + "|" + rs.getString("month"), rs.getLong("active"));
        });

        List<FunnelDtos.Cohort> cohorts = new ArrayList<>();
        for (YearMonth cohort = first; !cohort.isAfter(current); cohort = cohort.plusMonths(1)) {
            long size = sizes.getOrDefault(cohort, 0L);
            List<FunnelDtos.CohortMonth> monthsActive = new ArrayList<>();
            int offset = 0;
            for (YearMonth month = cohort; !month.isAfter(current); month = month.plusMonths(1), offset++) {
                long count = active.getOrDefault(cohort + "|" + month, 0L);
                monthsActive.add(new FunnelDtos.CohortMonth(offset, count, percent(count, size)));
            }
            cohorts.add(new FunnelDtos.Cohort(cohort.toString(), size, monthsActive));
        }
        return new FunnelDtos.Retention(span, cohorts);
    }

    @Override
    @Transactional
    public int prune() {
        return jdbc.update("delete from catalog_events where created_at < :cutoff", new MapSqlParameterSource(
                "cutoff", Timestamp.from(Instant.now().minus(Duration.ofDays(properties.retentionDays())))));
    }

    /** Signed-in users, then the browser's own random id, then the network address: always salted and hashed. */
    private String visitorHash(UUID userId, String visitorId, String clientIp, String userAgent) {
        String key;
        if (userId != null) {
            key = "user:" + userId;
        } else if (visitorId != null && VISITOR_ID.matcher(visitorId).matches()) {
            key = "visitor:" + visitorId;
        } else {
            key = "client:" + clientIp + "|" + (userAgent == null ? "" : userAgent);
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.visitorSalt().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(key.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HmacSHA256 is not available", ex);
        }
    }

    private boolean allow(String clientIp) {
        if (!rateLimitProperties.enabled()) {
            return true;
        }
        Bucket bucket;
        synchronized (buckets) {
            bucket = buckets.computeIfAbsent(String.valueOf(clientIp), key -> Bucket.builder()
                    .addLimit(Bandwidth.builder()
                            .capacity(properties.perMinute())
                            .refillGreedy(properties.perMinute(), Duration.ofMinutes(1))
                            .build())
                    .build());
        }
        return bucket.tryConsume(1);
    }

    private static BigDecimal percent(long part, long whole) {
        return whole == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(part * 100.0 / whole).setScale(1, RoundingMode.HALF_UP);
    }
}
