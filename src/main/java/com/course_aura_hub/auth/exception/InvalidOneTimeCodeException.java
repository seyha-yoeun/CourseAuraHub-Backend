package com.course_aura_hub.auth.exception;

/**
 * A verification code, reset token or 2FA challenge was wrong, expired, already used or exhausted.
 * The message deliberately does not say which.
 */
public class InvalidOneTimeCodeException extends AuthException {

    public InvalidOneTimeCodeException(String message) {
        super(message);
    }
}
