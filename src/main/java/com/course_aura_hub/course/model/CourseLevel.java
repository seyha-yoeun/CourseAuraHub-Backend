package com.course_aura_hub.course.model;

import com.course_aura_hub.common.exception.BusinessRuleException;

import java.util.Locale;

public enum CourseLevel {
    BEGINNER("Beginner"),
    INTERMEDIATE("Intermediate"),
    ADVANCED("Advanced");

    private final String apiValue;

    CourseLevel(String apiValue) {
        this.apiValue = apiValue;
    }

    public static CourseLevel fromValue(String value) {
        if (value == null || value.isBlank()) {
            return BEGINNER;
        }
        try {
            return CourseLevel.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("Unsupported course level: " + value);
        }
    }

    public String apiValue() {
        return apiValue;
    }
}
