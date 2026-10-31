package com.course_aura_hub.coupon.service;

import com.course_aura_hub.common.exception.BusinessRuleException;
import com.course_aura_hub.common.exception.DuplicateResourceException;
import com.course_aura_hub.common.exception.ResourceNotFoundException;
import com.course_aura_hub.coupon.dto.CouponCreateRequest;
import com.course_aura_hub.coupon.dto.CouponPreviewRequest;
import com.course_aura_hub.coupon.dto.CouponPreviewResponse;
import com.course_aura_hub.coupon.dto.CouponResponse;
import com.course_aura_hub.coupon.dto.CouponUpdateRequest;
import com.course_aura_hub.coupon.model.Coupon;
import com.course_aura_hub.coupon.repository.CouponRepository;
import com.course_aura_hub.course.model.Course;
import com.course_aura_hub.course.model.CourseStatus;
import com.course_aura_hub.course.model.DiscountType;
import com.course_aura_hub.course.repository.CourseRepository;
import com.course_aura_hub.learner.service.LearnerAccountService;
import com.course_aura_hub.order.model.OrderStatus;
import com.course_aura_hub.order.repository.OrderRepository;
import com.course_aura_hub.student.model.Student;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponServiceImpl implements CouponService {

    // How long an unpaid checkout holds a use of the code
    private static final Duration RESERVATION = Duration.ofMinutes(30);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final String INVALID_COUPON = "This coupon code is not valid";
    private static final Integer NO_ORDER = -1;

    private final CouponRepository couponRepository;
    private final CourseRepository courseRepository;
    private final OrderRepository orderRepository;
    private final LearnerAccountService learnerAccountService;

    @Override
    @Transactional(readOnly = true)
    public List<CouponResponse> findAll() {
        return couponRepository.findAllByOrderByCreatedAtDescIdDesc().stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public CouponResponse create(CouponCreateRequest request) {
        String code = normalize(request.code());
        if (couponRepository.existsByCode(code)) {
            throw new DuplicateResourceException("Coupon code already exists");
        }

        DiscountType type = DiscountType.valueOf(request.discountType().toUpperCase(Locale.ROOT));
        if (type == DiscountType.PERCENT && request.discountValue().compareTo(HUNDRED) > 0) {
            throw new BusinessRuleException("A percentage discount cannot exceed 100");
        }
        if (request.startsAt() != null && request.expiresAt() != null
                && !request.expiresAt().isAfter(request.startsAt())) {
            throw new BusinessRuleException("Expiry must be after the start date");
        }

        Course course = StringUtils.hasText(request.courseSlug())
                ? courseRepository.findBySlug(request.courseSlug())
                        .orElseThrow(() -> ResourceNotFoundException.of("Course", request.courseSlug()))
                : null;

        Coupon saved = couponRepository.save(Coupon.builder()
                .code(code)
                .discountType(type)
                .discountValue(request.discountValue())
                .course(course)
                .maxRedemptions(request.maxRedemptions())
                .startsAt(request.startsAt())
                .expiresAt(request.expiresAt())
                .active(true)
                .build());
        log.info("Created coupon id={} code={}", saved.getId(), code);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public CouponResponse update(Integer id, CouponUpdateRequest request) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Coupon", id));
        if (request.active() != null) {
            coupon.setActive(request.active());
        }
        if (request.maxRedemptions() != null) {
            coupon.setMaxRedemptions(request.maxRedemptions());
        }
        if (request.expiresAt() != null) {
            coupon.setExpiresAt(request.expiresAt());
        }
        log.info("Updated coupon id={}", id);
        return toResponse(coupon);
    }

    @Override
    @Transactional(readOnly = true)
    public Course chooseCourse(String code, List<Course> courses) {
        Coupon coupon = couponRepository.findByCode(normalize(code))
                .orElseThrow(() -> new BusinessRuleException(INVALID_COUPON));
        return courses.stream()
                .filter(course -> coupon.getCourse() == null || coupon.getCourse().getId().equals(course.getId()))
                .max(Comparator.comparing(course -> course.currentPrice().price()))
                .orElseThrow(() -> new BusinessRuleException(INVALID_COUPON));
    }

    @Override
    @Transactional(readOnly = true)
    public CouponPreviewResponse preview(String email, CouponPreviewRequest request) {
        Course course = courseRepository.findBySlug(request.courseSlug())
                .filter(found -> found.getStatus() == CourseStatus.PUBLISHED)
                .orElseThrow(() -> ResourceNotFoundException.of("Course", request.courseSlug()));
        Integer studentId = learnerAccountService.findCurrentStudent(email).map(Student::getId).orElse(NO_ORDER);
        Coupon coupon = couponRepository.findByCode(normalize(request.code()))
                .orElseThrow(() -> new BusinessRuleException(INVALID_COUPON));

        CouponQuote quote = quote(coupon, course, studentId, NO_ORDER);
        return new CouponPreviewResponse(
                coupon.getCode(),
                quote.listAmount(),
                quote.discountAmount(),
                quote.finalAmount()
        );
    }

    @Override
    public CouponQuote applyToOrder(String code, Course course, Integer studentId, Integer excludeOrderId) {
        if (!StringUtils.hasText(code)) {
            return CouponQuote.none(course);
        }
        Coupon coupon = couponRepository.findByCodeForUpdate(normalize(code))
                .orElseThrow(() -> new BusinessRuleException(INVALID_COUPON));
        return quote(coupon, course, studentId, excludeOrderId == null ? NO_ORDER : excludeOrderId);
    }

    private CouponQuote quote(Coupon coupon, Course course, Integer studentId, Integer excludeOrderId) {
        // One message for every reason, so codes and their rules can't be probed
        Instant now = Instant.now();
        boolean started = coupon.getStartsAt() == null || !coupon.getStartsAt().isAfter(now);
        boolean expired = coupon.getExpiresAt() != null && !coupon.getExpiresAt().isAfter(now);
        boolean rightCourse = coupon.getCourse() == null || coupon.getCourse().getId().equals(course.getId());
        if (!coupon.isActive() || !started || expired || !rightCourse) {
            throw new BusinessRuleException(INVALID_COUPON);
        }

        if (orderRepository.countLearnerUses(coupon.getId(), studentId, excludeOrderId) > 0) {
            throw new BusinessRuleException("You have already used this coupon");
        }
        if (coupon.getMaxRedemptions() != null) {
            long uses = orderRepository.countActiveUses(coupon.getId(), excludeOrderId, now.minus(RESERVATION));
            if (uses >= coupon.getMaxRedemptions()) {
                throw new BusinessRuleException("This coupon has reached its limit");
            }
        }

        // A code stacks on a running sale
        BigDecimal list = course.getPrice();
        BigDecimal price = course.currentPrice().price();
        BigDecimal couponOff = coupon.getDiscountType().amountOff(price, coupon.getDiscountValue());
        return new CouponQuote(coupon, list, list.subtract(price).add(couponOff), price.subtract(couponOff));
    }

    private String normalize(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private CouponResponse toResponse(Coupon coupon) {
        return new CouponResponse(
                coupon.getId(),
                coupon.getCode(),
                coupon.getDiscountType().name(),
                coupon.getDiscountValue(),
                coupon.getCourse() == null ? null : coupon.getCourse().getSlug(),
                coupon.getMaxRedemptions(),
                orderRepository.countByCouponIdAndStatus(coupon.getId(), OrderStatus.PAID),
                coupon.getStartsAt(),
                coupon.getExpiresAt(),
                coupon.isActive(),
                coupon.getCreatedAt()
        );
    }
}
