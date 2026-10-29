package com.course_aura_hub.auth.email;

import java.time.Duration;

/**
 * Every transactional email the auth module sends. Each record carries only what its template needs.
 */
public sealed interface AuthEmail {

    String to();

    String firstName();

    record VerificationCode(String to, String firstName, String code, Duration validFor) implements AuthEmail {
    }

    /** Someone tried to register with an email that already has an account. */
    record ExistingAccountRegistration(String to, String firstName) implements AuthEmail {
    }

    record PasswordReset(String to, String firstName, String resetLink, Duration validFor) implements AuthEmail {
    }

    record PasswordChanged(String to, String firstName) implements AuthEmail {
    }

    record MfaEnabled(String to, String firstName) implements AuthEmail {
    }

    record MfaDisabled(String to, String firstName) implements AuthEmail {
    }
}
