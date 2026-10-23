package com.course_aura_hub.course.repository;

import com.course_aura_hub.course.model.CourseMonthlyMetric;
import com.course_aura_hub.course.model.CourseMonthlyMetricId;
import com.course_aura_hub.course.repository.projection.MonthlyMetricProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CourseMonthlyMetricRepository extends JpaRepository<CourseMonthlyMetric, CourseMonthlyMetricId> {

    List<CourseMonthlyMetric> findByCourseId(Integer courseId);

    Optional<CourseMonthlyMetric> findByCourseIdAndMonth(Integer courseId, Short month);

    @Query("""
            select m.month as month, sum(m.enrollments) as enrollments, sum(m.revenue) as revenue
            from CourseMonthlyMetric m
            group by m.month
            order by m.month
            """)
    List<MonthlyMetricProjection> aggregateByMonth();

    @Query("""
            select sum(m.enrollments)
            from CourseMonthlyMetric m
            where m.month = :month
            """)
    Long sumEnrollmentsForMonth(@Param("month") Short month);

    @Query("""
            select sum(m.revenue)
            from CourseMonthlyMetric m
            where m.month = :month
            """)
    java.math.BigDecimal sumRevenueForMonth(@Param("month") Short month);
}
