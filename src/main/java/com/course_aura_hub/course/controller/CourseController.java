package com.course_aura_hub.course.controller;

import com.course_aura_hub.common.dto.ApiResponse;
import com.course_aura_hub.common.security.SecurityUtils;
import com.course_aura_hub.course.dto.CourseCreateRequest;
import com.course_aura_hub.course.dto.CourseResponse;
import com.course_aura_hub.course.dto.CourseSearchCriteria;
import com.course_aura_hub.course.dto.CourseUpdateRequest;
import com.course_aura_hub.course.service.CourseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/courses")
@RequiredArgsConstructor
@Tag(name = "Courses", description = "Course catalogue and admin course management endpoints")
public class CourseController {

    private final CourseService courseService;

    @GetMapping
    @Operation(
            summary = "List courses",
            description = "Public. Visitors and learners receive published courses only, without sales figures. "
                    + "Admins can filter by any status."
    )
    public ResponseEntity<ApiResponse<List<CourseResponse>>> findAll(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String order,
            Authentication authentication
    ) {
        CourseSearchCriteria criteria = new CourseSearchCriteria(status, category, search, sort, order);
        boolean includeUnpublished = SecurityUtils.isAdmin(authentication);
        return ResponseEntity.ok(ApiResponse.success(courseService.findAll(criteria, includeUnpublished)));
    }

    @GetMapping("/{slug}")
    @Operation(
            summary = "Get course",
            description = "Public. Returns a published course and whether the caller is enrolled. "
                    + "Admins can see any status and also receive sales metrics and the enrolled-student list."
    )
    public ResponseEntity<ApiResponse<CourseResponse>> findBySlug(
            @PathVariable String slug,
            Authentication authentication
    ) {
        boolean admin = SecurityUtils.isAdmin(authentication);
        return ResponseEntity.ok(ApiResponse.success(courseService.findBySlug(slug, admin, SecurityUtils.emailOf(authentication))));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create course", description = "Admin-only course creation.")
    public ResponseEntity<ApiResponse<CourseResponse>> create(@Valid @RequestBody CourseCreateRequest request) {
        log.info("Received create course request title={}", request.title());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Course created successfully", courseService.create(request)));
    }

    @PutMapping("/{slug}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update course", description = "Admin-only partial course update.")
    public ResponseEntity<ApiResponse<CourseResponse>> update(
            @PathVariable String slug,
            @Valid @RequestBody CourseUpdateRequest request
    ) {
        log.info("Received update course request slug={}", slug);
        return ResponseEntity.ok(ApiResponse.success(
                "Course updated successfully",
                courseService.update(slug, request)
        ));
    }

    @DeleteMapping("/{slug}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete course", description = "Admin-only course deletion.")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String slug) {
        log.info("Received delete course request slug={}", slug);
        courseService.delete(slug);
        return ResponseEntity.ok(ApiResponse.success("Course deleted successfully", null));
    }
}
