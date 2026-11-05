package com.course_aura_hub.course.dto;

import java.math.BigDecimal;

/**
 * Catalog filters. Every field is optional.
 *
 * @param sort     revenue, totalStudents, rating, price, title, createdAt, updatedAt or relevance;
 *                 a search without a sort is ordered by relevance
 * @param minPrice lower bound on the price a learner pays now, after any running sale
 * @param free     true for free courses only, false for paid only
 * @param onSale   true for courses with a running sale
 */
public record CourseSearchCriteria(
        String status,
        String category,
        String search,
        String sort,
        String order,
        String level,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Boolean free,
        Boolean onSale,
        BigDecimal minRating,
        BigDecimal minHours,
        BigDecimal maxHours
) {

    public CourseSearchCriteria(String status, String category, String search, String sort, String order) {
        this(status, category, search, sort, order, null, null, null, null, null, null, null, null);
    }
}
