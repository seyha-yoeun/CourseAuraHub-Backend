package com.course_aura_hub.auth.exception;

public class MfaUnavailableException extends AuthException {

    public MfaUnavailableException() {
        super("Two-factor authentication is not available right now. Please try again later.");
    }
}
