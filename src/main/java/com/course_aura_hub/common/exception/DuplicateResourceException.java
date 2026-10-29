package com.course_aura_hub.common.exception;

/**
 * Raised when a unique domain constraint would be violated.
 */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
