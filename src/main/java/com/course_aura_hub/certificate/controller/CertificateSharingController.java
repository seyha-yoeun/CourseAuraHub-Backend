package com.course_aura_hub.certificate.controller;

import com.course_aura_hub.certificate.dto.LinkedInShareResponse;
import com.course_aura_hub.certificate.service.CertificateSharingService;
import com.course_aura_hub.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

/**
 * Open Badges documents are returned as plain JSON, not wrapped in ApiResponse, because badge wallets and
 * validators fetch them directly.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Certificate sharing", description = "Open Badges and LinkedIn sharing for course certificates")
public class CertificateSharingController {

    private final CertificateSharingService sharingService;

    @GetMapping(value = "/api/v1/certificates/{certificateNumber}/badge.json", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Open Badges assertion", description = "Public. Open Badges 2.0 hosted assertion; the "
            + "learner's email appears only as a salted SHA-256 hash.")
    public ResponseEntity<Map<String, Object>> assertion(@PathVariable String certificateNumber) {
        return cached(sharingService.assertion(certificateNumber));
    }

    @GetMapping(value = "/api/v1/badges/courses/{slug}.json", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Open Badges badge class", description = "Public. The badge a course's certificates award.")
    public ResponseEntity<Map<String, Object>> badgeClass(@PathVariable String slug) {
        return cached(sharingService.badgeClass(slug));
    }

    @GetMapping(value = "/api/v1/badges/issuer.json", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Open Badges issuer", description = "Public. This platform as a badge issuer.")
    public ResponseEntity<Map<String, Object>> issuer() {
        return cached(sharingService.issuer());
    }

    @GetMapping("/api/v1/me/certificates/{certificateNumber}/linkedin-url")
    @Operation(summary = "Add my certificate to LinkedIn", description = "A link to LinkedIn's add-certification "
            + "form, filled in with the course, issue date and verification URL.")
    public ResponseEntity<ApiResponse<LinkedInShareResponse>> linkedIn(@PathVariable String certificateNumber,
                                                                       Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(sharingService.linkedInShare(authentication.getName(), certificateNumber)));
    }

    private static ResponseEntity<Map<String, Object>> cached(Map<String, Object> body) {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic()).body(body);
    }
}
