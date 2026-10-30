package com.course_aura_hub.auth.exception;

import java.util.Locale;

/**
 * OAuth2 sign-in could not complete. Only {@link Reason#code()} is sent to the browser.
 */
public class OAuth2LoginException extends AuthException {

    private final Reason reason;

    public OAuth2LoginException(Reason reason) {
        super("OAuth2 login rejected: " + reason.name());
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }

    public enum Reason {
        UNSUPPORTED_PROVIDER,
        INVALID_PROFILE,
        EMAIL_REQUIRED,
        /** An account with this email exists and the provider did not prove ownership of the email. */
        ACCOUNT_LINK_REQUIRED,
        /** The account is already linked to a different identity at this provider. */
        ACCOUNT_LINK_CONFLICT,
        ACCOUNT_UNAVAILABLE,
        ACCOUNT_LOCKED;

        public String code() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
