package com.course_aura_hub.common.exception;

/**
 * Raised when an authenticated user is not allowed to perform an action on a resource,
 * for example tracking progress in a course they are not enrolled in.
 */
public class ForbiddenActionException extends RuntimeException {

    public ForbiddenActionException(String message) {
        super(message);
    }
}
