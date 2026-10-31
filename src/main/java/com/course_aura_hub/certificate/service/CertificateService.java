package com.course_aura_hub.certificate.service;

import com.course_aura_hub.certificate.dto.CertificateResponse;
import com.course_aura_hub.certificate.dto.CertificateVerificationResponse;

import java.util.List;

/**
 * Issues and looks up course completion certificates. A certificate is issued once, the first time a learner
 * completes a course, and is kept even if the course's lessons change afterwards.
 */
public interface CertificateService {

    /**
     * Issues a certificate for the learner and course unless one exists. Must run inside the caller's transaction.
     *
     * @return {@code true} when a new certificate was issued
     */
    boolean issueIfAbsent(Integer studentId, Integer courseId);

    /**
     * Issues certificates to every learner marked as having completed the course who does not hold one yet,
     * for example after an admin removed the last lessons they had not finished.
     *
     * @return number of certificates issued
     */
    int issueForCompletedLearners(Integer courseId);

    /**
     * Certificates held by the authenticated user, newest first.
     */
    List<CertificateResponse> findMine(String email);

    /**
     * Public verification lookup by certificate number (case-insensitive).
     */
    CertificateVerificationResponse verify(String certificateNumber);

    /**
     * The certificate as a printable PDF, for the learner who earned it. Anyone else's reads as missing.
     *
     * @param language {@code km} for Khmer wording, anything else for English
     */
    byte[] renderPdf(String email, String certificateNumber, String language);
}
