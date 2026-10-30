package com.course_aura_hub.auth.exception;

/**
 * A rotated refresh token was presented again; its whole session family has been revoked.
 */
public class RefreshTokenReuseException extends InvalidTokenException {

    public RefreshTokenReuseException() {
        super("Invalid refresh token");
    }
}
