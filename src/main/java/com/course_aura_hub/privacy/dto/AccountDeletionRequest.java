package com.course_aura_hub.privacy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param password     required for accounts that have a password
 * @param confirmation the account email, typed again; required for every account
 */
public record AccountDeletionRequest(
        @Size(max = 100) String password,
        @NotBlank @Size(max = 150) String confirmation
) {
}
