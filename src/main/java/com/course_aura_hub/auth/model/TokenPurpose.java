package com.course_aura_hub.auth.model;

/**
 * What a one-time secret in {@code auth_one_time_tokens} may be used for. Numeric secrets are short
 * codes typed by the user; opaque secrets are long random tokens carried in links or API calls.
 */
public enum TokenPurpose {
    EMAIL_VERIFICATION(true),
    PASSWORD_RESET(false),
    MFA_LOGIN_CHALLENGE(false),
    TELEGRAM_LINK(false);

    private final boolean numeric;

    TokenPurpose(boolean numeric) {
        this.numeric = numeric;
    }

    public boolean isNumeric() {
        return numeric;
    }
}
