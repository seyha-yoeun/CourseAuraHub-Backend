package com.course_aura_hub.course.model;

import java.math.BigDecimal;

/**
 * What a course costs right now.
 *
 * @param price         what a learner pays
 * @param originalPrice the crossed-out price
 * @param discount      the running sale, or null
 */
public record CoursePrice(BigDecimal price, BigDecimal originalPrice, CourseDiscount discount) {
}
