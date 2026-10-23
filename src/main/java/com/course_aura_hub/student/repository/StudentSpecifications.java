package com.course_aura_hub.student.repository;

import com.course_aura_hub.student.model.Student;
import com.course_aura_hub.student.model.StudentStatus;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

public final class StudentSpecifications {

    private StudentSpecifications() {
    }

    public static Specification<Student> status(StudentStatus status) {
        return (root, query, cb) -> status == null
                ? cb.conjunction()
                : cb.equal(root.get("status"), status);
    }

    public static Specification<Student> search(String search) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(search)) {
                return cb.conjunction();
            }

            String pattern = "%" + search.trim().toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(root.get("email")), pattern),
                    cb.like(cb.lower(root.get("country")), pattern)
            );
        };
    }
}
