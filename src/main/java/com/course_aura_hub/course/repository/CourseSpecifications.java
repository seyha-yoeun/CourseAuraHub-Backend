package com.course_aura_hub.course.repository;

import com.course_aura_hub.course.model.Course;
import com.course_aura_hub.course.model.CourseLevel;
import com.course_aura_hub.course.model.CourseStatus;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Collection;

public final class CourseSpecifications {

    private CourseSpecifications() {
    }

    public static Specification<Course> status(CourseStatus status) {
        return (root, query, cb) -> status == null
                ? cb.conjunction()
                : cb.equal(root.get("status"), status);
    }

    public static Specification<Course> category(String category) {
        return (root, query, cb) -> !StringUtils.hasText(category)
                ? cb.conjunction()
                : cb.equal(cb.lower(root.get("category")), category.trim().toLowerCase());
    }

    public static Specification<Course> level(CourseLevel level) {
        return (root, query, cb) -> level == null
                ? cb.conjunction()
                : cb.equal(root.get("level"), level);
    }

    public static Specification<Course> minRating(BigDecimal minRating) {
        return (root, query, cb) -> minRating == null
                ? cb.conjunction()
                : cb.greaterThanOrEqualTo(root.get("rating"), minRating);
    }

    public static Specification<Course> hoursBetween(BigDecimal minHours, BigDecimal maxHours) {
        return (root, query, cb) -> cb.and(
                minHours == null ? cb.conjunction() : cb.greaterThanOrEqualTo(root.get("totalHours"), minHours),
                maxHours == null ? cb.conjunction() : cb.lessThanOrEqualTo(root.get("totalHours"), maxHours)
        );
    }

    /** Limits results to ids that a full-text search already ranked. */
    public static Specification<Course> idIn(Collection<Integer> ids) {
        return (root, query, cb) -> ids == null ? cb.conjunction() : root.get("id").in(ids);
    }
}
