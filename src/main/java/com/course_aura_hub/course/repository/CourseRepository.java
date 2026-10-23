package com.course_aura_hub.course.repository;

import com.course_aura_hub.course.model.Course;
import com.course_aura_hub.course.model.CourseStatus;
import com.course_aura_hub.course.repository.projection.CategoryRevenueProjection;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface CourseRepository extends JpaRepository<Course, Integer>, JpaSpecificationExecutor<Course> {

    Optional<Course> findBySlug(String slug);

    boolean existsBySlug(String slug);

    long countByStatus(CourseStatus status);

    @Query("select coalesce(sum(c.revenue), 0) from Course c")
    BigDecimal sumRevenue();

    @Query("select coalesce(avg(c.rating), 0) from Course c where c.rating > 0")
    BigDecimal averageRating();

    List<Course> findTop5ByOrderByRevenueDesc();

    @Query("""
            select c.category as category, sum(c.revenue) as revenue
            from Course c
            group by c.category
            order by sum(c.revenue) desc
            """)
    List<CategoryRevenueProjection> sumRevenueByCategory();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Course c where c.id = :id")
    Optional<Course> findByIdForUpdate(@Param("id") Integer id);
}
