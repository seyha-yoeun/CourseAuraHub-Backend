package com.course_aura_hub.certificate.service;

import com.course_aura_hub.certificate.config.CertificateProperties;
import com.course_aura_hub.certificate.config.CertificateSharingProperties;
import com.course_aura_hub.certificate.dto.LinkedInShareResponse;
import com.course_aura_hub.certificate.model.CourseCertificate;
import com.course_aura_hub.certificate.repository.CourseCertificateRepository;
import com.course_aura_hub.common.exception.ResourceNotFoundException;
import com.course_aura_hub.course.model.Course;
import com.course_aura_hub.course.repository.CourseRepository;
import com.course_aura_hub.learner.service.LearnerAccountService;
import com.course_aura_hub.settings.service.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CertificateSharingServiceImpl implements CertificateSharingService {

    private static final String CONTEXT = "https://w3id.org/openbadges/v2";
    private static final String LINKEDIN_ADD = "https://www.linkedin.com/profile/add";

    private final CourseCertificateRepository certificateRepository;
    private final CourseRepository courseRepository;
    private final LearnerAccountService learnerAccountService;
    private final SettingsService settingsService;
    private final CertificateSharingProperties sharing;
    private final CertificateProperties certificates;

    @Override
    public Map<String, Object> assertion(String certificateNumber) {
        CourseCertificate certificate = find(certificateNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Certificate not found"));
        String number = certificate.getCertificateNumber();

        // Open Badges hashes the recipient, so the document never shows the learner's address
        Map<String, Object> recipient = new LinkedHashMap<>();
        recipient.put("type", "email");
        recipient.put("hashed", true);
        recipient.put("salt", number);
        recipient.put("identity", "sha256$" + sha256(certificate.getStudent().getEmail().toLowerCase(Locale.ROOT) + number));

        Map<String, Object> verification = new LinkedHashMap<>();
        verification.put("type", "HostedBadge");

        Map<String, Object> assertion = new LinkedHashMap<>();
        assertion.put("@context", CONTEXT);
        assertion.put("type", "Assertion");
        assertion.put("id", assertionUrl(number));
        assertion.put("recipient", recipient);
        assertion.put("badge", badgeClassUrl(certificate.getCourse().getSlug()));
        assertion.put("issuedOn", certificate.getIssuedAt().toString());
        assertion.put("verification", verification);
        assertion.put("evidence", verifyUrl(number));
        assertion.put("narrative", certificate.getLearnerName() + " completed " + certificate.getCourseTitle() + ".");
        return assertion;
    }

    @Override
    public Map<String, Object> badgeClass(String courseSlug) {
        Course course = courseRepository.findBySlug(courseSlug)
                .filter(found -> certificateRepository.existsByCourseId(found.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Badge not found"));

        Map<String, Object> criteria = new LinkedHashMap<>();
        criteria.put("narrative", "Complete every lesson of " + course.getTitle() + ".");

        Map<String, Object> badge = new LinkedHashMap<>();
        badge.put("@context", CONTEXT);
        badge.put("type", "BadgeClass");
        badge.put("id", badgeClassUrl(course.getSlug()));
        badge.put("name", course.getTitle());
        badge.put("description", StringUtils.hasText(course.getDescription())
                ? course.getDescription() : "Completed " + course.getTitle() + ".");
        badge.put("image", StringUtils.hasText(course.getThumbnail()) ? course.getThumbnail() : sharing.badgeImageUrl());
        badge.put("criteria", criteria);
        badge.put("issuer", issuerUrl());
        return badge;
    }

    @Override
    public Map<String, Object> issuer() {
        Map<String, Object> issuer = new LinkedHashMap<>();
        issuer.put("@context", CONTEXT);
        issuer.put("type", "Issuer");
        issuer.put("id", issuerUrl());
        issuer.put("name", platformName());
        issuer.put("url", sharing.siteUrl());
        if (StringUtils.hasText(sharing.issuerEmail())) {
            issuer.put("email", sharing.issuerEmail());
        }
        return issuer;
    }

    @Override
    public LinkedInShareResponse linkedInShare(String email, String certificateNumber) {
        CourseCertificate certificate = learnerAccountService.findCurrentStudent(email)
                .flatMap(student -> find(certificateNumber)
                        .filter(found -> found.getStudent().getId().equals(student.getId())))
                .orElseThrow(() -> new ResourceNotFoundException("Certificate not found"));
        String number = certificate.getCertificateNumber();
        ZonedDateTime issued = certificate.getIssuedAt().atZone(ZoneOffset.UTC);

        UriComponentsBuilder link = UriComponentsBuilder.fromUriString(LINKEDIN_ADD)
                .queryParam("startTask", "CERTIFICATION_NAME")
                .queryParam("name", certificate.getCourseTitle())
                .queryParam("issueYear", issued.getYear())
                .queryParam("issueMonth", issued.getMonthValue())
                .queryParam("certUrl", verifyUrl(number))
                .queryParam("certId", number);
        if (StringUtils.hasText(sharing.linkedinOrganizationId())) {
            link.queryParam("organizationId", sharing.linkedinOrganizationId());
        } else {
            link.queryParam("organizationName", platformName());
        }
        return new LinkedInShareResponse(link.encode().build().toUriString(), number, verifyUrl(number));
    }

    private Optional<CourseCertificate> find(String certificateNumber) {
        return Optional.ofNullable(CertificateNumberGenerator.normalize(certificateNumber))
                .flatMap(certificateRepository::findByCertificateNumber);
    }

    private String platformName() {
        return settingsService.getPublicSettings().platformName();
    }

    private String assertionUrl(String number) {
        return sharing.apiBaseUrl() + "/certificates/" + number + "/badge.json";
    }

    private String badgeClassUrl(String slug) {
        return sharing.apiBaseUrl() + "/badges/courses/" + slug + ".json";
    }

    private String issuerUrl() {
        return sharing.apiBaseUrl() + "/badges/issuer.json";
    }

    private String verifyUrl(String number) {
        return certificates.verifyUrl().replaceAll("/+$", "") + "/" + number;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
