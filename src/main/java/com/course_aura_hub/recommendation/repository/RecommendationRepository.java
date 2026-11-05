package com.course_aura_hub.recommendation.repository;

import com.course_aura_hub.course.model.Course;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Ranks published courses for recommendations. Scores are computed in SQL so only ids travel back:
 * 3 points per shared tag, 2 for a shared category and up to 5 for learners who took both courses.
 * Ties fall back to rating and popularity, so a course with no signal still gets popular suggestions.
 */
@org.springframework.stereotype.Repository
public interface RecommendationRepository extends Repository<Course, Integer> {

    @Query(value = """
            with seed_tags as (
                select distinct lower(tag) as tag from course_tags where course_id = :courseId
            ),
            co as (
                select b.course_id, count(*) as learners
                from student_courses a
                join student_courses b on b.student_id = a.student_id and b.course_id <> a.course_id
                where a.course_id = :courseId
                group by b.course_id
            )
            select c.id
            from courses c
            left join co on co.course_id = c.id
            where c.status = 'PUBLISHED' and c.id <> :courseId
            order by (
                3 * (select count(distinct lower(t.tag)) from course_tags t
                     where t.course_id = c.id and lower(t.tag) in (select tag from seed_tags))
                + case when lower(c.category) = lower(:category) then 2 else 0 end
                + least(coalesce(co.learners, 0), 5)
            ) desc, c.rating desc, c.total_students desc, c.id
            limit :limit
            """, nativeQuery = true)
    List<Integer> findRelatedCourseIds(
            @Param("courseId") Integer courseId,
            @Param("category") String category,
            @Param("limit") int limit
    );

    /**
     * Seeds are the learner's enrolled and wishlisted courses; both are excluded from the results.
     * A learner with no seeds (or no student row, id -1) gets the most popular courses.
     */
    @Query(value = """
            with seeds as (
                select course_id from student_courses where student_id = :studentId
                union
                select course_id from wishlist_items where student_id = :studentId
            ),
            seed_tags as (
                select distinct lower(t.tag) as tag from course_tags t join seeds s on s.course_id = t.course_id
            ),
            seed_categories as (
                select distinct lower(c.category) as category from courses c join seeds s on s.course_id = c.id
            ),
            co as (
                select b.course_id, count(distinct b.student_id) as learners
                from student_courses a
                join student_courses b on b.student_id = a.student_id
                where a.course_id in (select course_id from seeds) and a.student_id <> :studentId
                group by b.course_id
            )
            select c.id
            from courses c
            left join co on co.course_id = c.id
            where c.status = 'PUBLISHED' and c.id not in (select course_id from seeds)
            order by (
                3 * (select count(distinct lower(t.tag)) from course_tags t
                     where t.course_id = c.id and lower(t.tag) in (select tag from seed_tags))
                + case when lower(c.category) in (select category from seed_categories) then 2 else 0 end
                + least(coalesce(co.learners, 0), 5)
            ) desc, c.rating desc, c.total_students desc, c.id
            limit :limit
            """, nativeQuery = true)
    List<Integer> findCourseIdsForStudent(@Param("studentId") Integer studentId, @Param("limit") int limit);
}
