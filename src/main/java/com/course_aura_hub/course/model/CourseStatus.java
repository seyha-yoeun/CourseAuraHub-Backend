package com.course_aura_hub.course.model;

import com.course_aura_hub.common.exception.BusinessRuleException;

import java.util.Locale;

public enum CourseStatus {
    DRAFT,
    PUBLISHED,
    ARCHIVED;

    public static CourseStatus fromValue(String value) {
        if (value == null || value.isBlank()) {
            return DRAFT;
        }
        try {
            return CourseStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("Unsupported course status: " + value);
        }
    }

    public String apiValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
