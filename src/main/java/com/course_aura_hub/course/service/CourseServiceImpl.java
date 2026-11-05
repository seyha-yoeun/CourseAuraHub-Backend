package com.course_aura_hub.course.service;

import com.course_aura_hub.common.exception.BusinessRuleException;
import com.course_aura_hub.common.exception.ResourceNotFoundException;
import com.course_aura_hub.course.dto.CourseCreateRequest;
import com.course_aura_hub.course.dto.CourseResponse;
import com.course_aura_hub.course.dto.CourseSearchCriteria;
import com.course_aura_hub.course.dto.CourseUpdateRequest;
import com.course_aura_hub.course.mapper.CourseAssembler;
import com.course_aura_hub.course.mapper.CourseMapper;
import com.course_aura_hub.course.model.Course;
import com.course_aura_hub.course.model.CourseLevel;
import com.course_aura_hub.course.model.CourseMonthlyMetric;
import com.course_aura_hub.course.model.CoursePrice;
import com.course_aura_hub.course.model.CourseStatus;
import com.course_aura_hub.course.repository.CourseRepository;
import com.course_aura_hub.course.repository.CourseSearchRepository;
import com.course_aura_hub.course.repository.CourseSpecifications;
import com.course_aura_hub.student.repository.StudentCourseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseServiceImpl implements CourseService {

    private static final String DEFAULT_INSTRUCTOR = "Alex Chen";
    private static final String DEFAULT_CATEGORY = "Web Development";
    private static final String DEFAULT_THUMBNAIL =
            "https://images.unsplash.com/photo-1516321318423-f06f85e504b3?w=800&h=450&fit=crop";
    private static final int MONTHS_IN_YEAR = 12;
    private static final BigDecimal MAX_RATING = BigDecimal.valueOf(5);

    private final CourseRepository courseRepository;
    private final StudentCourseRepository studentCourseRepository;
    private final CourseMapper courseMapper;
    private final CourseAssembler courseAssembler;
    private final CourseSlugService courseSlugService;
    private final CourseSearchRepository courseSearchRepository;

    @Override
    @Transactional(readOnly = true)
    public List<CourseResponse> findAll(CourseSearchCriteria criteria, boolean includeUnpublished) {
        CourseStatus status = includeUnpublished
                ? parseStatus(criteria.status())
                : CourseStatus.PUBLISHED;
        validateRanges(criteria);

        // A search is ranked in SQL first; the other filters then narrow those ids
        List<Integer> rankedIds = null;
        if (StringUtils.hasText(criteria.search())) {
            String query = criteria.search().trim();
            rankedIds = courseSearchRepository.findRankedIds(query, likePattern(query));
            if (rankedIds.isEmpty()) {
                return List.of();
            }
        }

        Specification<Course> specification = Specification.allOf(
                CourseSpecifications.status(status),
                CourseSpecifications.category(criteria.category()),
                CourseSpecifications.level(StringUtils.hasText(criteria.level()) ? CourseLevel.fromValue(criteria.level()) : null),
                CourseSpecifications.minRating(criteria.minRating()),
                CourseSpecifications.hoursBetween(criteria.minHours(), criteria.maxHours()),
                CourseSpecifications.idIn(rankedIds)
        );

        List<Course> courses = courseRepository.findAll(specification, resolveSort(criteria));
        if (rankedIds != null && sortsByRelevance(criteria)) {
            Map<Integer, Integer> rank = new HashMap<>();
            for (int i = 0; i < rankedIds.size(); i++) {
                rank.put(rankedIds.get(i), i);
            }
            courses = courses.stream().sorted(Comparator.comparing(course -> rank.get(course.getId()))).toList();
        }

        // Price filters use what a learner pays now, so they run after the running sale is applied
        List<Course> filtered = courses.stream().filter(course -> matchesPrice(course, criteria)).toList();
        log.debug("Fetched {} courses (includeUnpublished={}, search={})",
                filtered.size(), includeUnpublished, rankedIds != null);
        return filtered.stream()
                .map(course -> includeUnpublished
                        ? courseAssembler.toResponse(course, null)
                        : courseAssembler.toPublicResponse(course, null))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CourseResponse findBySlug(String slug, boolean admin, String viewerEmail) {
        Course course = courseRepository.findBySlug(slug)
                .orElseThrow(() -> ResourceNotFoundException.of("Course", slug));

        if (!admin && course.getStatus() != CourseStatus.PUBLISHED) {
            throw ResourceNotFoundException.of("Course", slug);
        }

        boolean enrolled = viewerEmail != null && studentCourseRepository.isUserEnrolled(viewerEmail, course.getId());

        log.debug("Fetched course slug={} admin={} enrolled={}", slug, admin, enrolled);
        if (!admin) {
            return courseAssembler.toPublicResponse(course, enrolled);
        }
        // The enrolled-student list contains learner PII (emails, spending) and is admin-only
        return courseAssembler.toResponse(course, studentCourseRepository.findStudentsByCourseId(course.getId()), enrolled);
    }

    @Override
    @Transactional
    public CourseResponse create(CourseCreateRequest request) {
        Course course = courseMapper.toEntity(request);
        LocalDate today = LocalDate.now();

        course.setSlug(courseSlugService.generateUniqueSlug(request.title()));
        course.setTitle(request.title().trim());
        course.setDescription(defaultText(request.description(), ""));
        course.setInstructor(defaultText(request.instructor(), DEFAULT_INSTRUCTOR));
        course.setPrice(defaultAmount(request.price(), BigDecimal.ZERO));
        course.setOriginalPrice(defaultAmount(request.originalPrice(), course.getPrice()));
        course.setStatus(CourseStatus.fromValue(request.status()));
        course.setCategory(defaultText(request.category(), DEFAULT_CATEGORY));
        course.setLevel(CourseLevel.fromValue(request.level()));
        course.setThumbnail(defaultText(request.thumbnail(), DEFAULT_THUMBNAIL));
        course.setRating(BigDecimal.ZERO);
        course.setTotalStudents(0);
        course.setTotalLessons(request.totalLessons() == null ? 0 : request.totalLessons());
        course.setTotalHours(defaultAmount(request.totalHours(), BigDecimal.ZERO));
        course.setRevenue(BigDecimal.ZERO);
        course.setCompletionRate(BigDecimal.ZERO);
        course.setCreatedAt(today);
        course.setUpdatedAt(today);
        course.replaceTags(sanitizeTags(request.tags()));

        for (short month = 1; month <= MONTHS_IN_YEAR; month++) {
            course.addMonthlyMetric(CourseMonthlyMetric.builder()
                    .month(month)
                    .enrollments(0)
                    .revenue(BigDecimal.ZERO)
                    .build());
        }

        Course saved = courseRepository.save(course);
        log.info("Created course id={} slug={} status={}", saved.getId(), saved.getSlug(), saved.getStatus());
        return courseAssembler.toResponse(saved, null);
    }

    @Override
    @Transactional
    public CourseResponse update(String slug, CourseUpdateRequest request) {
        Course course = courseRepository.findBySlug(slug)
                .orElseThrow(() -> ResourceNotFoundException.of("Course", slug));

        if (request.title() != null && !StringUtils.hasText(request.title())) {
            throw new BusinessRuleException("Course title cannot be blank");
        }

        courseMapper.updateEntity(request, course);

        if (request.status() != null) {
            course.setStatus(CourseStatus.fromValue(request.status()));
        }
        if (request.level() != null) {
            course.setLevel(CourseLevel.fromValue(request.level()));
        }
        if (request.title() != null) {
            course.setTitle(request.title().trim());
        }
        if (request.tags() != null) {
            course.replaceTags(sanitizeTags(request.tags()));
        }
        course.setUpdatedAt(LocalDate.now());

        Course saved = courseRepository.save(course);
        log.info("Updated course id={} slug={}", saved.getId(), saved.getSlug());
        return courseAssembler.toResponse(saved, null);
    }

    @Override
    @Transactional
    public void delete(String slug) {
        Course course = courseRepository.findBySlug(slug)
                .orElseThrow(() -> ResourceNotFoundException.of("Course", slug));
        courseRepository.delete(course);
        log.info("Deleted course id={} slug={}", course.getId(), slug);
    }

    private CourseStatus parseStatus(String rawStatus) {
        if (!StringUtils.hasText(rawStatus) || "all".equalsIgnoreCase(rawStatus)) {
            return null;
        }
        return CourseStatus.fromValue(rawStatus);
    }

    private static boolean sortsByRelevance(CourseSearchCriteria criteria) {
        return !StringUtils.hasText(criteria.sort()) || "relevance".equals(criteria.sort());
    }

    private static boolean matchesPrice(Course course, CourseSearchCriteria criteria) {
        CoursePrice current = course.currentPrice();
        BigDecimal price = current.price();
        if (criteria.minPrice() != null && price.compareTo(criteria.minPrice()) < 0) {
            return false;
        }
        if (criteria.maxPrice() != null && price.compareTo(criteria.maxPrice()) > 0) {
            return false;
        }
        if (criteria.free() != null && (price.signum() == 0) != criteria.free()) {
            return false;
        }
        return criteria.onSale() == null || (current.discount() != null) == criteria.onSale();
    }

    private static void validateRanges(CourseSearchCriteria criteria) {
        requireNonNegative(criteria.minPrice(), "minPrice");
        requireNonNegative(criteria.maxPrice(), "maxPrice");
        requireNonNegative(criteria.minHours(), "minHours");
        requireNonNegative(criteria.maxHours(), "maxHours");
        if (criteria.minRating() != null
                && (criteria.minRating().signum() < 0 || criteria.minRating().compareTo(MAX_RATING) > 0)) {
            throw new BusinessRuleException("minRating must be between 0 and 5");
        }
        if (criteria.minPrice() != null && criteria.maxPrice() != null
                && criteria.minPrice().compareTo(criteria.maxPrice()) > 0) {
            throw new BusinessRuleException("minPrice cannot be greater than maxPrice");
        }
        if (criteria.minHours() != null && criteria.maxHours() != null
                && criteria.minHours().compareTo(criteria.maxHours()) > 0) {
            throw new BusinessRuleException("minHours cannot be greater than maxHours");
        }
    }

    private static void requireNonNegative(BigDecimal value, String name) {
        if (value != null && value.signum() < 0) {
            throw new BusinessRuleException(name + " cannot be negative");
        }
    }

    /** Lower-case substring pattern with LIKE wildcards in the search escaped. */
    private static String likePattern(String search) {
        String escaped = search.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private Sort resolveSort(CourseSearchCriteria criteria) {
        String requested = StringUtils.hasText(criteria.sort()) ? criteria.sort() : "createdAt";
        String field = switch (requested) {
            case "revenue", "totalStudents", "rating", "price", "title", "createdAt", "updatedAt" -> requested;
            default -> "createdAt";
        };
        Sort.Direction direction = "asc".equalsIgnoreCase(criteria.order())
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        return Sort.by(direction, field);
    }

    private List<String> sanitizeTags(List<String> tags) {
        if (tags == null) {
            return new ArrayList<>();
        }
        return tags.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .limit(20)
                .toList();
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private BigDecimal defaultAmount(BigDecimal value, BigDecimal fallback) {
        return value == null ? fallback : value;
    }
}
