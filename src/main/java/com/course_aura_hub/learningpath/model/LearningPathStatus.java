package com.course_aura_hub.learningpath.model;

import com.course_aura_hub.common.exception.BusinessRuleException;

import java.util.Locale;

public enum LearningPathStatus {
    DRAFT,
    PUBLISHED;

    public static LearningPathStatus fromValue(String value) {
        if (value == null || value.isBlank()) {
            return DRAFT;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("Status must be draft or published");
        }
    }

    public String apiValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
