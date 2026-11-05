package com.course_aura_hub.learningpath.controller;

import com.course_aura_hub.certificate.service.CertificateNumberGenerator;
import com.course_aura_hub.common.dto.ApiResponse;
import com.course_aura_hub.common.exception.ResourceNotFoundException;
import com.course_aura_hub.learningpath.dto.LearningPathEnrollResponse;
import com.course_aura_hub.learningpath.dto.LearningPathProgressResponse;
import com.course_aura_hub.learningpath.dto.LearningPathRequest;
import com.course_aura_hub.learningpath.dto.LearningPathResponse;
import com.course_aura_hub.learningpath.service.LearningPathService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Learning paths", description = "Ordered sets of courses with their own completion certificate")
public class LearningPathController {

    private static final String ADMIN = "/api/v1/admin/learning-paths";
    private static final String MINE = "/api/v1/me/learning-paths";

    private final LearningPathService pathService;

    @GetMapping("/api/v1/learning-paths")
    @Operation(summary = "Learning paths", description = "Public. Published paths with their published courses and combined price.")
    public ResponseEntity<ApiResponse<List<LearningPathResponse>>> published() {
        return ResponseEntity.ok(ApiResponse.success(pathService.findPublished()));
    }

    @GetMapping("/api/v1/learning-paths/{slug}")
    @Operation(summary = "Learning path", description = "Public. One published path.")
    public ResponseEntity<ApiResponse<LearningPathResponse>> publishedOne(@PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.success(pathService.findPublished(slug)));
    }

    @GetMapping(MINE)
    @Operation(summary = "My learning paths", description = "Published paths I've started, with progress.")
    public ResponseEntity<ApiResponse<List<LearningPathProgressResponse>>> mine(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(pathService.findMine(authentication.getName())));
    }

    @GetMapping(MINE + "/{slug}/progress")
    @Operation(
            summary = "My progress through a path",
            description = "Course by course. Finishing every course issues the path certificate."
    )
    public ResponseEntity<ApiResponse<LearningPathProgressResponse>> progress(
            @PathVariable String slug,
            Authentication authentication
    ) {
        return ResponseEntity.ok(ApiResponse.success(pathService.progress(authentication.getName(), slug)));
    }

    @PostMapping(MINE + "/{slug}/enroll")
    @Operation(
            summary = "Enroll in a path",
            description = "Enrolls me in the path's free courses and adds its paid courses to my cart for checkout."
    )
    public ResponseEntity<ApiResponse<LearningPathEnrollResponse>> enroll(
            @PathVariable String slug,
            Authentication authentication
    ) {
        return ResponseEntity.ok(ApiResponse.success(pathService.enroll(authentication.getName(), slug)));
    }

    @GetMapping(MINE + "/certificates/{certificateNumber}/pdf")
    @Operation(
            summary = "Download my path certificate",
            description = "Printable PDF. lang=km for Khmer wording. Verify it publicly at /api/v1/certificates/{number}."
    )
    public ResponseEntity<byte[]> certificatePdf(
            @PathVariable String certificateNumber,
            @RequestParam(defaultValue = "en") String lang,
            Authentication authentication
    ) {
        String number = CertificateNumberGenerator.normalize(certificateNumber);
        if (number == null) {
            throw new ResourceNotFoundException("Certificate not found");
        }
        byte[] pdf = pathService.certificatePdf(authentication.getName(), number, lang);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("path-certificate-" + number + ".pdf").build().toString())
                .body(pdf);
    }

    @GetMapping(ADMIN)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "All learning paths", description = "Admin-only. Every status, with every course.")
    public ResponseEntity<ApiResponse<List<LearningPathResponse>>> all() {
        return ResponseEntity.ok(ApiResponse.success(pathService.findAllForAdmin()));
    }

    @PostMapping(ADMIN)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a learning path", description = "Admin-only. A published path needs a published course.")
    public ResponseEntity<ApiResponse<LearningPathResponse>> create(@Valid @RequestBody LearningPathRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Learning path created", pathService.create(request)));
    }

    @PutMapping(ADMIN + "/{slug}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a learning path", description = "Admin-only. courseSlugs replaces the course list, in order.")
    public ResponseEntity<ApiResponse<LearningPathResponse>> update(
            @PathVariable String slug,
            @Valid @RequestBody LearningPathRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Learning path updated", pathService.update(slug, request)));
    }

    @DeleteMapping(ADMIN + "/{slug}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a learning path", description = "Admin-only. Path certificates already issued are kept.")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String slug) {
        pathService.delete(slug);
        return ResponseEntity.ok(ApiResponse.success("Learning path deleted", null));
    }
}
