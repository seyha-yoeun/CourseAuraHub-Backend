package com.course_aura_hub.enrollment.controller;

import com.course_aura_hub.common.dto.ApiResponse;
import com.course_aura_hub.enrollment.dto.EnrollmentCreateRequest;
import com.course_aura_hub.enrollment.dto.EnrollmentResponse;
import com.course_aura_hub.enrollment.service.EnrollmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/enrollments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Enrollments", description = "Admin-only enrollment ledger endpoints")
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    @GetMapping
    @Operation(summary = "List recent enrollments", description = "Returns the most recent enrollment transactions.")
    public ResponseEntity<ApiResponse<List<EnrollmentResponse>>> findRecent(
            @RequestParam(defaultValue = "20") int limit
    ) {
        return ResponseEntity.ok(ApiResponse.success(enrollmentService.findRecent(limit)));
    }

    @PostMapping
    @Operation(summary = "Create enrollment", description = "Creates the enrollment ledger row and updates aggregate snapshots.")
    public ResponseEntity<ApiResponse<EnrollmentResponse>> create(
            @Valid @RequestBody EnrollmentCreateRequest request
    ) {
        log.info("Received create enrollment request studentId={} courseId={}", request.studentId(), request.courseId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Enrollment created successfully", enrollmentService.create(request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete enrollment", description = "Deletes an enrollment and rolls back aggregate snapshots.")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer id) {
        log.info("Received delete enrollment request id={}", id);
        enrollmentService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Enrollment deleted successfully", null));
    }
}
