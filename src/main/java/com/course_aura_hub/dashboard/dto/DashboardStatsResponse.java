package com.course_aura_hub.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record DashboardStatsResponse(
        Kpis kpis,
        Charts charts,
        List<TopCourse> topCourses,
        List<RecentEnrollment> recentEnrollments
) {

    public record Kpis(
            BigDecimal totalRevenue,
            int totalStudents,
            int totalCourses,
            int publishedCourses,
            BigDecimal avgRating,
            BigDecimal revenueGrowth,
            BigDecimal studentGrowth
    ) {
    }

    public record Charts(
            List<String> months,
            List<BigDecimal> monthlyRevenue,
            List<Integer> monthlyEnrollments,
            Map<String, BigDecimal> revenueByCategory
    ) {
    }

    public record TopCourse(
            String slug,
            String title,
            BigDecimal revenue,
            Integer totalStudents,
            BigDecimal rating,
            String category,
            BigDecimal price,
            String status
    ) {
    }

    public record RecentEnrollment(
            Integer id,
            String studentName,
            String studentAvatar,
            String studentCountry,
            String courseTitle,
            String courseSlug,
            BigDecimal amount,
            LocalDate enrolledAt
    ) {
    }
}
