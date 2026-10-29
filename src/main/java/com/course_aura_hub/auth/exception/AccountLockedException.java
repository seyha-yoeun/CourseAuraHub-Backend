package com.course_aura_hub.auth.exception;

public class AccountLockedException extends AuthException {

    public AccountLockedException() {
        super("Account temporarily locked due to too many failed sign-in attempts. Try again later.");
    }
}
