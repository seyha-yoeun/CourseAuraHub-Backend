package com.course_aura_hub.course.service;

import com.course_aura_hub.common.exception.BusinessRuleException;
import com.course_aura_hub.common.exception.DuplicateResourceException;
import com.course_aura_hub.common.exception.ResourceNotFoundException;
import com.course_aura_hub.course.dto.CourseDiscountCreateRequest;
import com.course_aura_hub.course.dto.CourseDiscountResponse;
import com.course_aura_hub.course.dto.CourseDiscountUpdateRequest;
import com.course_aura_hub.course.model.Course;
import com.course_aura_hub.course.model.CourseDiscount;
import com.course_aura_hub.course.model.DiscountType;
import com.course_aura_hub.course.repository.CourseDiscountRepository;
import com.course_aura_hub.course.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseDiscountServiceImpl implements CourseDiscountService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final CourseDiscountRepository discountRepository;
    private final CourseRepository courseRepository;

    @Override
    @Transactional(readOnly = true)
    public List<CourseDiscountResponse> findAll(String courseSlug) {
        List<CourseDiscount> discounts = StringUtils.hasText(courseSlug)
                ? discountRepository.findByCourseSlugOrderByCreatedAtDescIdDesc(courseSlug)
                : discountRepository.findAllByOrderByCreatedAtDescIdDesc();
        return discounts.stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public CourseDiscountResponse create(CourseDiscountCreateRequest request) {
        Course course = lockCourse(courseRepository.findBySlug(request.courseSlug())
                .orElseThrow(() -> ResourceNotFoundException.of("Course", request.courseSlug())));
        if (course.getPrice().signum() <= 0) {
            throw new BusinessRuleException("A free course cannot be discounted");
        }

        DiscountType type = parseType(request.discountType());
        Instant startsAt = request.startsAt() == null ? Instant.now() : request.startsAt();
        validate(type, request.discountValue(), startsAt, request.endsAt());
        if (request.endsAt() != null && !request.endsAt().isAfter(Instant.now())) {
            throw new BusinessRuleException("End date must be in the future");
        }
        ensureNoOverlap(course, null, startsAt, request.endsAt());

        CourseDiscount saved = discountRepository.save(CourseDiscount.builder()
                .course(course)
                .discountType(type)
                .discountValue(request.discountValue())
                .startsAt(startsAt)
                .endsAt(request.endsAt())
                .active(true)
                .build());
        log.info("Created course discount id={} courseId={}", saved.getId(), course.getId());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public CourseDiscountResponse update(Integer id, CourseDiscountUpdateRequest request) {
        CourseDiscount discount = discountRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Course discount", id));
        Course course = lockCourse(discount.getCourse());

        DiscountType type = request.discountType() == null ? discount.getDiscountType() : parseType(request.discountType());
        BigDecimal value = Objects.requireNonNullElse(request.discountValue(), discount.getDiscountValue());
        Instant startsAt = Objects.requireNonNullElse(request.startsAt(), discount.getStartsAt());
        Instant endsAt = request.endsAt() == null ? discount.getEndsAt() : request.endsAt();
        boolean active = request.active() == null ? discount.isActive() : request.active();

        validate(type, value, startsAt, endsAt);
        if (active) {
            ensureNoOverlap(course, id, startsAt, endsAt);
        }

        discount.setDiscountType(type);
        discount.setDiscountValue(value);
        discount.setStartsAt(startsAt);
        discount.setEndsAt(endsAt);
        discount.setActive(active);
        log.info("Updated course discount id={}", id);
        return toResponse(discount);
    }

    @Override
    @Transactional
    public void delete(Integer id) {
        CourseDiscount discount = discountRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Course discount", id));
        discountRepository.delete(discount);
        log.info("Deleted course discount id={}", id);
    }

    // Serializes admin edits, so two overlapping sales can't be saved at once
    private Course lockCourse(Course course) {
        return courseRepository.findByIdForUpdate(course.getId())
                .orElseThrow(() -> ResourceNotFoundException.of("Course", course.getId()));
    }

    private void validate(DiscountType type, BigDecimal value, Instant startsAt, Instant endsAt) {
        if (type == DiscountType.PERCENT && value.compareTo(HUNDRED) > 0) {
            throw new BusinessRuleException("A percentage discount cannot exceed 100");
        }
        if (endsAt != null && !endsAt.isAfter(startsAt)) {
            throw new BusinessRuleException("End date must be after the start date");
        }
    }

    private void ensureNoOverlap(Course course, Integer excludeId, Instant startsAt, Instant endsAt) {
        boolean overlapping = discountRepository.findByCourseIdAndActiveTrue(course.getId()).stream()
                .filter(other -> !other.getId().equals(excludeId))
                .anyMatch(other -> other.overlaps(startsAt, endsAt));
        if (overlapping) {
            throw new DuplicateResourceException("This course already has a discount in that period");
        }
    }

    private DiscountType parseType(String value) {
        return DiscountType.valueOf(value.toUpperCase(Locale.ROOT));
    }

    private CourseDiscountResponse toResponse(CourseDiscount discount) {
        Course course = discount.getCourse();
        return new CourseDiscountResponse(
                discount.getId(),
                course.getSlug(),
                course.getTitle(),
                discount.getDiscountType().name(),
                discount.getDiscountValue(),
                course.getPrice(),
                course.getPrice().subtract(discount.amountOff(course.getPrice())),
                discount.getStartsAt(),
                discount.getEndsAt(),
                discount.isActive(),
                discount.isRunningAt(Instant.now()),
                discount.getCreatedAt()
        );
    }
}
