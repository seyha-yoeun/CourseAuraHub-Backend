package com.course_aura_hub.certificate.controller;

import com.course_aura_hub.certificate.dto.CertificateResponse;
import com.course_aura_hub.certificate.dto.CertificateVerificationResponse;
import com.course_aura_hub.certificate.service.CertificateService;
import com.course_aura_hub.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Certificates", description = "Course completion certificates and public verification")
public class CertificateController {

    private final CertificateService certificateService;

    @GetMapping("/me/certificates")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "My certificates",
            description = "Certificates earned by completing courses, newest first. Kept even if a course changes later."
    )
    public ResponseEntity<ApiResponse<List<CertificateResponse>>> myCertificates(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(certificateService.findMine(authentication.getName())));
    }

    @GetMapping("/certificates/{certificateNumber}")
    @Operation(
            summary = "Verify a certificate",
            description = "Public. Confirms a certificate number and shows who earned it for which course. Case-insensitive."
    )
    public ResponseEntity<ApiResponse<CertificateVerificationResponse>> verify(@PathVariable String certificateNumber) {
        return ResponseEntity.ok(ApiResponse.success(certificateService.verify(certificateNumber)));
    }
}
