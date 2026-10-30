package com.course_aura_hub.learner.service;

import com.course_aura_hub.common.exception.BusinessRuleException;
import com.course_aura_hub.common.exception.ResourceNotFoundException;
import com.course_aura_hub.course.model.Course;
import com.course_aura_hub.course.model.CoursePrice;
import com.course_aura_hub.course.model.CourseStatus;
import com.course_aura_hub.course.repository.CourseRepository;
import com.course_aura_hub.learner.dto.WishlistItemResponse;
import com.course_aura_hub.learner.model.WishlistItem;
import com.course_aura_hub.learner.repository.WishlistItemRepository;
import com.course_aura_hub.student.model.Student;
import com.course_aura_hub.student.repository.StudentCourseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WishlistServiceImpl implements WishlistService {

    private final WishlistItemRepository wishlistItemRepository;
    private final CourseRepository courseRepository;
    private final StudentCourseRepository studentCourseRepository;
    private final LearnerAccountService learnerAccountService;

    @Override
    @Transactional(readOnly = true)
    public List<WishlistItemResponse> findMine(String email) {
        return learnerAccountService.findCurrentStudent(email)
                .map(student -> wishlistItemRepository.findByStudentIdOrderByCreatedAtDesc(student.getId())
                        .stream()
                        .map(this::toResponse)
                        .toList())
                .orElse(List.of());
    }

    @Override
    @Transactional
    public WishlistItemResponse add(String email, String courseSlug) {
        Course course = courseRepository.findBySlug(courseSlug)
                .filter(found -> found.getStatus() == CourseStatus.PUBLISHED)
                .orElseThrow(() -> ResourceNotFoundException.of("Course", courseSlug));
        Student student = learnerAccountService.currentStudent(email);

        if (studentCourseRepository.existsByStudentIdAndCourseId(student.getId(), course.getId())) {
            throw new BusinessRuleException("You are already enrolled in this course");
        }

        WishlistItem item = wishlistItemRepository.findByStudentIdAndCourseId(student.getId(), course.getId())
                .orElseGet(() -> {
                    log.info("Added course to wishlist studentId={} courseId={}", student.getId(), course.getId());
                    return wishlistItemRepository.save(WishlistItem.builder()
                            .student(student)
                            .course(course)
                            .build());
                });
        return toResponse(item);
    }

    @Override
    @Transactional
    public void remove(String email, String courseSlug) {
        Course course = courseRepository.findBySlug(courseSlug)
                .orElseThrow(() -> ResourceNotFoundException.of("Course", courseSlug));
        learnerAccountService.findCurrentStudent(email)
                .ifPresent(student -> removeIfPresent(student.getId(), course.getId()));
    }

    @Override
    @Transactional
    public void removeIfPresent(Integer studentId, Integer courseId) {
        wishlistItemRepository.findByStudentIdAndCourseId(studentId, courseId).ifPresent(item -> {
            wishlistItemRepository.delete(item);
            log.info("Removed course from wishlist studentId={} courseId={}", studentId, courseId);
        });
    }

    private WishlistItemResponse toResponse(WishlistItem item) {
        Course course = item.getCourse();
        CoursePrice current = course.currentPrice();
        return new WishlistItemResponse(
                course.getId(),
                course.getSlug(),
                course.getTitle(),
                course.getThumbnail(),
                course.getInstructor(),
                course.getLevel().apiValue(),
                current.price(),
                current.originalPrice(),
                course.getRating(),
                item.getCreatedAt()
        );
    }
}
