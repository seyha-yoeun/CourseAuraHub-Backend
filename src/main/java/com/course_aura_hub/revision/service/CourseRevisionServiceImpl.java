package com.course_aura_hub.revision.service;

import com.course_aura_hub.common.exception.BusinessRuleException;
import com.course_aura_hub.common.exception.ResourceNotFoundException;
import com.course_aura_hub.common.security.Actor;
import com.course_aura_hub.course.dto.CourseUpdateRequest;
import com.course_aura_hub.course.model.Course;
import com.course_aura_hub.course.model.CourseLevel;
import com.course_aura_hub.course.model.CourseStatus;
import com.course_aura_hub.course.service.CourseAccess;
import com.course_aura_hub.course.service.CourseService;
import com.course_aura_hub.notification.model.NotificationType;
import com.course_aura_hub.notification.service.NotificationService;
import com.course_aura_hub.revision.config.CourseRevisionProperties;
import com.course_aura_hub.revision.dto.RevisionDtos;
import com.course_aura_hub.revision.model.CourseRevision;
import com.course_aura_hub.revision.model.RevisionStatus;
import com.course_aura_hub.revision.repository.CourseRevisionRepository;
import com.course_aura_hub.student.repository.StudentCourseRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseRevisionServiceImpl implements CourseRevisionService {

    /** Fields an instructor can propose, in display order. */
    static final List<String> FIELDS = List.of("title", "description", "price", "originalPrice", "category", "level", "thumbnail", "tags");
    /** Changes learners are told about once approved. */
    private static final Set<String> LEARNER_VISIBLE = Set.of("title", "description", "level");
    private static final TypeReference<LinkedHashMap<String, Object>> MAP = new TypeReference<>() {
    };
    private static final String ADMIN_LINK = "/admin/course-reviews";

    private final CourseRevisionRepository repository;
    private final CourseService courseService;
    private final CourseAccess courseAccess;
    private final NotificationService notificationService;
    private final StudentCourseRepository studentCourseRepository;
    private final CourseRevisionProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void propose(Actor actor, Course course, CourseUpdateRequest request) {
        Map<String, Object> live = liveValues(course);
        Map<String, Object> requested = requestedValues(request);

        Map<String, Object> immediate = new LinkedHashMap<>();
        Map<String, Object> reviewed = new LinkedHashMap<>();
        requested.forEach((field, value) -> {
            if (!Objects.equals(value, live.get(field))) {
                (properties.immediateFields().contains(field) ? immediate : reviewed).put(field, value);
            }
        });
        if (!immediate.isEmpty()) {
            courseService.update(course.getSlug(), toRequest(immediate));
            log.info("Applied live-course change without review courseId={} fields={}", course.getId(), immediate.keySet());
        }

        CourseRevision pending = repository.findByCourseIdAndStatus(course.getId(), RevisionStatus.PENDING).orElse(null);
        Map<String, Object> merged = new LinkedHashMap<>(pending == null ? Map.of() : read(pending.getChanges()));
        // A field edited back to its live value drops out of the revision
        requested.forEach((field, value) -> {
            if (Objects.equals(value, live.get(field))) {
                merged.remove(field);
            }
        });
        merged.putAll(reviewed);
        merged.keySet().removeIf(properties.immediateFields()::contains);

        if (merged.isEmpty()) {
            if (pending != null) {
                pending.setStatus(RevisionStatus.WITHDRAWN);
            }
            return;
        }
        Map<String, Object> previous = new LinkedHashMap<>();
        merged.keySet().forEach(field -> previous.put(field, live.get(field)));
        if (pending == null) {
            pending = CourseRevision.builder().course(course).submittedBy(actor.userId()).status(RevisionStatus.PENDING).build();
            notificationService.notifyAdmins(NotificationType.COURSE_REVIEW, "Course changes waiting for review",
                    "%s proposed changes to %s.".formatted(course.getInstructor(), course.getTitle()), ADMIN_LINK);
        }
        pending.setChanges(write(merged));
        pending.setPrevious(write(previous));
        repository.save(pending);
        log.info("Course revision saved courseId={} fields={}", course.getId(), merged.keySet());
    }

    @Override
    @Transactional(readOnly = true)
    public RevisionDtos.RevisionResponse findPending(Integer courseId) {
        return repository.findByCourseIdAndStatus(courseId, RevisionStatus.PENDING).map(this::toResponse).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RevisionDtos.RevisionResponse> history(Actor actor, String slug) {
        Course course = managed(actor, slug);
        return repository.findByCourseIdOrderByCreatedAtDescIdDesc(course.getId()).stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public RevisionDtos.RevisionResponse updateSummary(Actor actor, String slug, String summary) {
        Course course = managed(actor, slug);
        CourseRevision pending = repository.findByCourseIdAndStatus(course.getId(), RevisionStatus.PENDING)
                .orElseThrow(() -> new BusinessRuleException("This course has no changes waiting for review"));
        pending.setSummary(StringUtils.hasText(summary) ? summary.trim() : null);
        return toResponse(pending);
    }

    @Override
    @Transactional
    public void withdraw(Actor actor, String slug) {
        Course course = managed(actor, slug);
        CourseRevision pending = repository.findByCourseIdAndStatus(course.getId(), RevisionStatus.PENDING)
                .orElseThrow(() -> new BusinessRuleException("This course has no changes waiting for review"));
        pending.setStatus(RevisionStatus.WITHDRAWN);
        log.info("Course revision withdrawn id={}", pending.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<RevisionDtos.RevisionResponse> findAwaitingReview() {
        return repository.findByStatusOrderByUpdatedAtAscIdAsc(RevisionStatus.PENDING).stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public RevisionDtos.RevisionResponse find(Integer id) {
        return repository.findById(id).map(this::toResponse).orElseThrow(() -> ResourceNotFoundException.of("Course revision", id));
    }

    @Override
    @Transactional
    public RevisionDtos.RevisionResponse approve(Integer id, UUID adminId, String note) {
        CourseRevision revision = requirePending(id);
        Course course = revision.getCourse();
        Map<String, Object> changes = read(revision.getChanges());
        courseService.update(course.getSlug(), toRequest(changes));

        revision.setStatus(RevisionStatus.APPROVED);
        revision.setReviewedBy(adminId);
        revision.setReviewedAt(Instant.now());
        revision.setReviewNote(StringUtils.hasText(note) ? note.trim() : null);
        notifyOwner(course, "Changes approved", "Your changes to %s are now live.".formatted(course.getTitle()));
        if (changes.keySet().stream().anyMatch(LEARNER_VISIBLE::contains)) {
            notificationService.notifyStudentsOnce(studentCourseRepository.findStudentIdsByCourseId(course.getId()),
                    NotificationType.COURSE_UPDATED, "Course updated",
                    StringUtils.hasText(revision.getSummary()) ? revision.getSummary()
                            : "%s was updated by its instructor.".formatted(course.getTitle()),
                    "/courses/" + course.getSlug());
        }
        log.info("Course revision approved id={} courseId={}", id, course.getId());
        return toResponse(revision);
    }

    @Override
    @Transactional
    public RevisionDtos.RevisionResponse reject(Integer id, UUID adminId, String note) {
        if (!StringUtils.hasText(note)) {
            throw new BusinessRuleException("Tell the instructor what to change");
        }
        CourseRevision revision = requirePending(id);
        revision.setStatus(RevisionStatus.REJECTED);
        revision.setReviewedBy(adminId);
        revision.setReviewedAt(Instant.now());
        revision.setReviewNote(note.trim());
        notifyOwner(revision.getCourse(), "Changes not approved", note.trim());
        log.info("Course revision rejected id={}", id);
        return toResponse(revision);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RevisionDtos.ChangelogEntry> changelog(String slug) {
        Course course = courseAccess.requireCourse(slug);
        if (course.getStatus() != CourseStatus.PUBLISHED) {
            throw ResourceNotFoundException.of("Course", slug);
        }
        return repository.findByCourseIdAndStatusOrderByReviewedAtDescIdDesc(course.getId(), RevisionStatus.APPROVED).stream()
                .map(revision -> new RevisionDtos.ChangelogEntry(revision.getReviewedAt(),
                        // Price moves aren't changelog news, and showing them would leak past prices
                        read(revision.getChanges()).keySet().stream()
                                .filter(field -> !field.equals("price") && !field.equals("originalPrice")).toList(),
                        revision.getSummary()))
                .filter(entry -> !entry.fields().isEmpty() || entry.summary() != null)
                .toList();
    }

    private CourseRevision requirePending(Integer id) {
        CourseRevision revision = repository.findByIdForUpdate(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Course revision", id));
        if (revision.getStatus() != RevisionStatus.PENDING) {
            throw new BusinessRuleException("This revision was already decided");
        }
        return revision;
    }

    private Course managed(Actor actor, String slug) {
        Course course = courseAccess.requireCourse(slug);
        courseAccess.requireManager(course, actor);
        return course;
    }

    private void notifyOwner(Course course, String title, String message) {
        if (course.getOwnerId() != null) {
            notificationService.notifyUsers(List.of(course.getOwnerId()), NotificationType.COURSE_REVIEW, title, message,
                    "/instructor/courses/" + course.getSlug());
        }
    }

    /** The live values of every revisable field, normalised the way requests are compared. */
    private static Map<String, Object> liveValues(Course course) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("title", course.getTitle());
        values.put("description", course.getDescription());
        values.put("price", money(course.getPrice()));
        values.put("originalPrice", money(course.getOriginalPrice()));
        values.put("category", course.getCategory());
        values.put("level", course.getLevel().apiValue());
        values.put("thumbnail", course.getThumbnail());
        values.put("tags", List.copyOf(course.getTags()));
        return values;
    }

    /** Only the fields the request sets. */
    private static Map<String, Object> requestedValues(CourseUpdateRequest request) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (request.title() != null) values.put("title", request.title().trim());
        if (request.description() != null) values.put("description", request.description().trim());
        if (request.price() != null) values.put("price", money(request.price()));
        if (request.originalPrice() != null) values.put("originalPrice", money(request.originalPrice()));
        if (request.category() != null) values.put("category", request.category().trim());
        if (request.level() != null) values.put("level", CourseLevel.fromValue(request.level()).apiValue());
        if (request.thumbnail() != null) values.put("thumbnail", request.thumbnail().trim());
        if (request.tags() != null) {
            values.put("tags", List.copyOf(new LinkedHashSet<>(request.tags().stream()
                    .filter(StringUtils::hasText).map(String::trim).limit(20).toList())));
        }
        return values;
    }

    @SuppressWarnings("unchecked")
    private static CourseUpdateRequest toRequest(Map<String, Object> changes) {
        return new CourseUpdateRequest(
                (String) changes.get("title"),
                (String) changes.get("description"),
                null,
                decimal(changes.get("price")),
                decimal(changes.get("originalPrice")),
                null,
                (String) changes.get("category"),
                (String) changes.get("level"),
                (String) changes.get("thumbnail"),
                null,
                null,
                changes.containsKey("tags") ? new ArrayList<>((List<String>) changes.get("tags")) : null
        );
    }

    private RevisionDtos.RevisionResponse toResponse(CourseRevision revision) {
        Map<String, Object> changes = read(revision.getChanges());
        Map<String, Object> previous = read(revision.getPrevious());
        List<RevisionDtos.FieldChange> fields = FIELDS.stream()
                .filter(changes::containsKey)
                .map(field -> new RevisionDtos.FieldChange(field, previous.get(field), changes.get(field)))
                .toList();
        return new RevisionDtos.RevisionResponse(revision.getId(), revision.getCourse().getSlug(), revision.getCourse().getTitle(),
                revision.getStatus().name().toLowerCase(java.util.Locale.ROOT), revision.getSummary(), fields,
                revision.getReviewNote(), revision.getCreatedAt(), revision.getUpdatedAt(), revision.getReviewedAt());
    }

    // Money is kept as a plain string so JSON never rounds it
    private static String money(BigDecimal value) {
        return value == null ? null : value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private static BigDecimal decimal(Object value) {
        return value == null ? null : new BigDecimal(value.toString());
    }

    private Map<String, Object> read(String json) {
        try {
            return objectMapper.readValue(json, MAP);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unreadable course revision", ex);
        }
    }

    private String write(Map<String, Object> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unwritable course revision", ex);
        }
    }
}
