package com.course_aura_hub.student.model;

import com.course_aura_hub.common.exception.BusinessRuleException;

import java.util.Locale;

public enum StudentStatus {
    ACTIVE,
    INACTIVE;

    public static StudentStatus fromValue(String value) {
        if (value == null || value.isBlank()) {
            return ACTIVE;
        }
        try {
            return StudentStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("Unsupported student status: " + value);
        }
    }

    public String apiValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
