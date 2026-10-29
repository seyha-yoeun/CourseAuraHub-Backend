package com.course_aura_hub.auth.service;

import com.course_aura_hub.auth.dto.AuthResponse;
import com.course_aura_hub.auth.dto.RegisterRequest;
import com.course_aura_hub.auth.email.AuthEmail;

import java.util.Optional;

/**
 * Local sign-up with email ownership proven by a one-time code.
 *
 * <p>Methods return the email to send (if any) instead of sending it, so delivery happens after
 * the transaction commits.</p>
 */
public interface RegistrationService {

    /**
     * Creates or refreshes a pending account. Returns the email to deliver: a verification code for
     * new/pending accounts, a notice for existing accounts, or empty when throttled. Callers must
     * respond identically in every case.
     */
    Optional<AuthEmail> register(RegisterRequest request);

    /** Verifies the code, activates the account and signs the user in. */
    AuthResponse verifyEmail(String email, String code);

    /** Issues a fresh code for a pending account; empty when not eligible or throttled. */
    Optional<AuthEmail> resendVerificationCode(String email);
}
