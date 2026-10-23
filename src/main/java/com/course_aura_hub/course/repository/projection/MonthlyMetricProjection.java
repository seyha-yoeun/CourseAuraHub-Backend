package com.course_aura_hub.course.repository.projection;

import java.math.BigDecimal;

public interface MonthlyMetricProjection {

    Short getMonth();

    Long getEnrollments();

    BigDecimal getRevenue();
}
