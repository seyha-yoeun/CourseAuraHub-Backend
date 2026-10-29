package com.course_aura_hub.auth.service;

import com.course_aura_hub.auth.email.AuthEmail;

import java.util.Optional;

/**
 * Forgot-password lifecycle. Results never reveal whether an email has an account.
 */
public interface PasswordResetService {

    /** Returns the reset email to deliver, or empty when not eligible or throttled. */
    Optional<AuthEmail> requestReset(String email);

    /**
     * Sets the new password, revokes every session and access token, and returns the
     * password-changed notification to deliver.
     */
    AuthEmail resetPassword(String token, String newPassword);
}
