package com.course_aura_hub.instructor.repository;

import com.course_aura_hub.instructor.model.InstructorProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InstructorProfileRepository extends JpaRepository<InstructorProfile, UUID> {

    Optional<InstructorProfile> findByHandle(String handle);

    boolean existsByHandle(String handle);

    @Query(value = """
            select count(*) from course_reviews r
            join courses c on c.id = r.course_id
            where c.owner_id = :ownerId and c.status = 'PUBLISHED'
            """, nativeQuery = true)
    long countPublishedReviews(@Param("ownerId") UUID ownerId);

    /** Profiles of teaching accounts with at least one published course, most learners first. */
    @Query(value = """
            select p.* from instructor_profiles p
            join users u on u.id = p.user_id
            join courses c on c.owner_id = p.user_id and c.status = 'PUBLISHED'
            where u.role in ('ROLE_INSTRUCTOR', 'ROLE_ADMIN') and u.enabled
            group by p.user_id
            order by sum(c.total_students) desc, p.handle
            limit :limit
            """, nativeQuery = true)
    List<InstructorProfile> findListed(@Param("limit") int limit);
}
