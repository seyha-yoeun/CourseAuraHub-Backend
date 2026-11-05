package com.course_aura_hub.privacy.service;

import com.course_aura_hub.privacy.dto.DataExportResponse;

import java.util.UUID;

public interface AccountPrivacyService {

    DataExportResponse export(UUID userId);

    /**
     * Removes personal data and closes the account. Orders, member card ledgers and certificates are
     * money and verification records, so they stay, attached to an anonymous learner.
     */
    void deleteAccount(UUID userId, String password, String confirmation);
}
