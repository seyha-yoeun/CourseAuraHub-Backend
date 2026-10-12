package com.course_aura_hub.auth.exception;

public class TokenExpiredException extends AuthException {

    public TokenExpiredException(String message) {
        super(message);
    }
}
