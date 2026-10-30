package com.course_aura_hub.curriculum.controller;

import com.course_aura_hub.common.dto.ApiResponse;
import com.course_aura_hub.common.security.SecurityUtils;
import com.course_aura_hub.curriculum.dto.CurriculumResponse;
import com.course_aura_hub.curriculum.dto.LessonCreateRequest;
import com.course_aura_hub.curriculum.dto.LessonResponse;
import com.course_aura_hub.curriculum.dto.LessonUpdateRequest;
import com.course_aura_hub.curriculum.dto.SectionCreateRequest;
import com.course_aura_hub.curriculum.dto.SectionResponse;
import com.course_aura_hub.curriculum.dto.SectionUpdateRequest;
import com.course_aura_hub.curriculum.service.CurriculumService;
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
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/courses/{slug}")
@RequiredArgsConstructor
@Tag(name = "Curriculum", description = "Course sections and lessons. Reads for learners, writes for admins.")
public class CurriculumController {

    private final CurriculumService curriculumService;

    @GetMapping("/curriculum")
    @Operation(
            summary = "Get course curriculum",
            description = "Public. Sections and lessons in order. Content URLs are returned only for preview lessons, "
                    + "enrolled learners, and admins. Includes the caller's progress when enrolled."
    )
    public ResponseEntity<ApiResponse<CurriculumResponse>> getCurriculum(
            @PathVariable String slug,
            Authentication authentication
    ) {
        return ResponseEntity.ok(ApiResponse.success(curriculumService.getCurriculum(
                slug,
                SecurityUtils.emailOf(authentication),
                SecurityUtils.isAdmin(authentication)
        )));
    }

    @PostMapping("/sections")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create section", description = "Admin-only. Appends to the end when no position is given.")
    public ResponseEntity<ApiResponse<SectionResponse>> createSection(
            @PathVariable String slug,
            @Valid @RequestBody SectionCreateRequest request
    ) {
        log.info("Received create section request courseSlug={}", slug);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Section created successfully", curriculumService.createSection(slug, request)));
    }

    @PutMapping("/sections/{sectionId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update section", description = "Admin-only partial update.")
    public ResponseEntity<ApiResponse<SectionResponse>> updateSection(
            @PathVariable String slug,
            @PathVariable Integer sectionId,
            @Valid @RequestBody SectionUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Section updated successfully",
                curriculumService.updateSection(slug, sectionId, request)
        ));
    }

    @DeleteMapping("/sections/{sectionId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete section", description = "Admin-only. Deletes the section and its lessons.")
    public ResponseEntity<ApiResponse<Void>> deleteSection(@PathVariable String slug, @PathVariable Integer sectionId) {
        log.info("Received delete section request courseSlug={} sectionId={}", slug, sectionId);
        curriculumService.deleteSection(slug, sectionId);
        return ResponseEntity.ok(ApiResponse.success("Section deleted successfully", null));
    }

    @PostMapping("/sections/{sectionId}/lessons")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create lesson", description = "Admin-only. Updates the course's lesson and hour totals.")
    public ResponseEntity<ApiResponse<LessonResponse>> createLesson(
            @PathVariable String slug,
            @PathVariable Integer sectionId,
            @Valid @RequestBody LessonCreateRequest request
    ) {
        log.info("Received create lesson request courseSlug={} sectionId={}", slug, sectionId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Lesson created successfully",
                        curriculumService.createLesson(slug, sectionId, request)
                ));
    }

    @PutMapping("/lessons/{lessonId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update lesson", description = "Admin-only partial update.")
    public ResponseEntity<ApiResponse<LessonResponse>> updateLesson(
            @PathVariable String slug,
            @PathVariable Integer lessonId,
            @Valid @RequestBody LessonUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Lesson updated successfully",
                curriculumService.updateLesson(slug, lessonId, request)
        ));
    }

    @DeleteMapping("/lessons/{lessonId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete lesson", description = "Admin-only. Updates course totals and learner completion.")
    public ResponseEntity<ApiResponse<Void>> deleteLesson(@PathVariable String slug, @PathVariable Integer lessonId) {
        log.info("Received delete lesson request courseSlug={} lessonId={}", slug, lessonId);
        curriculumService.deleteLesson(slug, lessonId);
        return ResponseEntity.ok(ApiResponse.success("Lesson deleted successfully", null));
    }
}
