package com.course_aura_hub.cart.service;

import com.course_aura_hub.cart.dto.CartCouponPreviewResponse;
import com.course_aura_hub.cart.dto.CartItemResponse;
import com.course_aura_hub.cart.dto.CartResponse;
import com.course_aura_hub.cart.model.CartItem;
import com.course_aura_hub.cart.repository.CartItemRepository;
import com.course_aura_hub.common.exception.BusinessRuleException;
import com.course_aura_hub.common.exception.DuplicateResourceException;
import com.course_aura_hub.common.exception.ResourceNotFoundException;
import com.course_aura_hub.coupon.dto.CouponPreviewRequest;
import com.course_aura_hub.coupon.dto.CouponPreviewResponse;
import com.course_aura_hub.coupon.service.CouponService;
import com.course_aura_hub.course.model.Course;
import com.course_aura_hub.course.model.CourseStatus;
import com.course_aura_hub.course.repository.CourseRepository;
import com.course_aura_hub.learner.service.LearnerAccountService;
import com.course_aura_hub.order.config.PaymentProperties;
import com.course_aura_hub.student.model.Student;
import com.course_aura_hub.student.repository.StudentCourseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartItemRepository cartItemRepository;
    private final CourseRepository courseRepository;
    private final StudentCourseRepository studentCourseRepository;
    private final LearnerAccountService learnerAccountService;
    private final CouponService couponService;
    private final PaymentProperties paymentProperties;

    @Override
    @Transactional(readOnly = true)
    public CartResponse findMine(String email) {
        return learnerAccountService.findCurrentStudent(email)
                .map(student -> toResponse(purchasableCourses(student.getId())))
                .orElseGet(() -> toResponse(List.of()));
    }

    @Override
    @Transactional
    public CartResponse add(String email, String courseSlug) {
        Student student = learnerAccountService.currentStudent(email);
        Course course = courseRepository.findBySlug(courseSlug)
                .filter(found -> found.getStatus() == CourseStatus.PUBLISHED)
                .orElseThrow(() -> ResourceNotFoundException.of("Course", courseSlug));

        if (course.currentPrice().price().signum() <= 0) {
            throw new BusinessRuleException("This course is free; enroll directly instead");
        }
        if (studentCourseRepository.existsByStudentIdAndCourseId(student.getId(), course.getId())) {
            throw new DuplicateResourceException("You are already enrolled in this course");
        }
        if (!cartItemRepository.existsByStudentIdAndCourseId(student.getId(), course.getId())) {
            cartItemRepository.save(CartItem.builder().student(student).course(course).build());
        }
        return toResponse(purchasableCourses(student.getId()));
    }

    @Override
    @Transactional
    public CartResponse remove(String email, String courseSlug) {
        Student student = learnerAccountService.currentStudent(email);
        courseRepository.findBySlug(courseSlug).ifPresent(course ->
                cartItemRepository.deleteByStudentIdAndCourseId(student.getId(), course.getId()));
        return toResponse(purchasableCourses(student.getId()));
    }

    @Override
    @Transactional
    public CartCouponPreviewResponse previewCoupon(String email, String code) {
        Student student = learnerAccountService.currentStudent(email);
        List<Course> courses = purchasableCourses(student.getId());
        Course target = couponService.chooseCourse(code, courses);
        CouponPreviewResponse quote = couponService.preview(email, new CouponPreviewRequest(target.getSlug(), code));

        BigDecimal couponDiscount = target.currentPrice().price().subtract(quote.finalAmount());
        return new CartCouponPreviewResponse(
                quote.code(),
                target.getSlug(),
                target.getTitle(),
                couponDiscount,
                total(courses).subtract(couponDiscount)
        );
    }

    /** Items that can still be bought; a course unpublished after it was added is left out. */
    private List<Course> purchasableCourses(Integer studentId) {
        return cartItemRepository.findByStudentIdOrderByAddedAtDescIdDesc(studentId).stream()
                .map(CartItem::getCourse)
                .filter(course -> course.getStatus() == CourseStatus.PUBLISHED)
                .toList();
    }

    private BigDecimal total(List<Course> courses) {
        return courses.stream().map(course -> course.currentPrice().price()).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private CartResponse toResponse(List<Course> courses) {
        List<CartItemResponse> items = courses.stream()
                .map(course -> new CartItemResponse(
                        course.getId(),
                        course.getSlug(),
                        course.getTitle(),
                        course.getThumbnail(),
                        course.getInstructor(),
                        course.getPrice(),
                        course.currentPrice().price()
                ))
                .toList();
        BigDecimal listTotal = courses.stream().map(Course::getPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CartResponse(items, listTotal, total(courses), paymentProperties.currency());
    }
}
