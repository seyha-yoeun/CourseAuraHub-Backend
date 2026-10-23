package com.course_aura_hub.common.exception;

/**
 * Raised when a requested domain resource does not exist.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException of(String resource, Object identifier) {
        return new ResourceNotFoundException(resource + " not found: " + identifier);
    }
}
