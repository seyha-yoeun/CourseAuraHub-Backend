package com.course_aura_hub.auth.exception;

public class AccountDisabledException extends AuthException {

    public AccountDisabledException() {
        super("This account is unavailable. Please contact support.");
    }
}
