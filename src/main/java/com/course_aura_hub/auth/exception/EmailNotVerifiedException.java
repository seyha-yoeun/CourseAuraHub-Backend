package com.course_aura_hub.auth.exception;

public class EmailNotVerifiedException extends AuthException {

    public EmailNotVerifiedException() {
        super("Email verification required. Enter the code sent to your email or request a new one.");
    }
}
