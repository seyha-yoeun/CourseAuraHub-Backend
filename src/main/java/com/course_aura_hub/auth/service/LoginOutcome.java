package com.course_aura_hub.auth.service;

import com.course_aura_hub.auth.dto.AuthResponse;
import com.course_aura_hub.auth.dto.MfaChallengeResponse;

/**
 * Result of a successful credential check: either tokens, or a second-factor challenge.
 */
public sealed interface LoginOutcome {

    record Authenticated(AuthResponse auth) implements LoginOutcome {
    }

    record MfaRequired(MfaChallengeResponse challenge) implements LoginOutcome {
    }
}
