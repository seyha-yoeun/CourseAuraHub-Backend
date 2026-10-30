package com.course_aura_hub.curriculum.model;

import com.course_aura_hub.common.exception.BusinessRuleException;

import java.util.Locale;

public enum LessonType {
    VIDEO,
    ARTICLE,
    QUIZ;

    public static LessonType fromValue(String value) {
        if (value == null || value.isBlank()) {
            return VIDEO;
        }
        try {
            return LessonType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("Unsupported lesson type: " + value);
        }
    }

    public String apiValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
