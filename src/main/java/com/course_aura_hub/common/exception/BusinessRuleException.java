package com.course_aura_hub.common.exception;

/**
 * Raised when a request is syntactically valid but violates a domain rule.
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
