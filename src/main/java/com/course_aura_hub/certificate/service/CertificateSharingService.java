package com.course_aura_hub.certificate.service;

import com.course_aura_hub.certificate.dto.LinkedInShareResponse;

import java.util.Map;

/**
 * Lets learners show certificates elsewhere. The Open Badges documents are public and hosted here, so any badge
 * wallet can verify them; the learner's email only appears as a salted hash.
 */
public interface CertificateSharingService {

    /** Open Badges 2.0 Assertion for a course certificate. */
    Map<String, Object> assertion(String certificateNumber);

    /** Open Badges 2.0 BadgeClass for a course. Only courses with at least one certificate have one. */
    Map<String, Object> badgeClass(String courseSlug);

    /** Open Badges 2.0 Issuer profile for this platform. */
    Map<String, Object> issuer();

    /** A LinkedIn add-to-profile link for the caller's own certificate. Anyone else's reads as missing. */
    LinkedInShareResponse linkedInShare(String email, String certificateNumber);
}
