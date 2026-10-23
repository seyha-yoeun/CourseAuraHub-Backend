package com.course_aura_hub.course.repository;

import com.course_aura_hub.course.model.Course;
import com.course_aura_hub.course.model.CourseStatus;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

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

    public static Specification<Course> search(String search) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(search)) {
                return cb.conjunction();
            }

            query.distinct(true);
            String pattern = "%" + search.trim().toLowerCase() + "%";
            jakarta.persistence.criteria.Join<Course, String> tags = root.join("tags", JoinType.LEFT);

            return cb.or(
                    cb.like(cb.lower(root.get("title")), pattern),
                    cb.like(cb.lower(root.get("description")), pattern),
                    cb.like(cb.lower(root.get("instructor")), pattern),
                    cb.like(cb.lower(tags), pattern)
            );
        };
    }
}
