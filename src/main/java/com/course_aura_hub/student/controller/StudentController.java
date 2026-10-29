package com.course_aura_hub.student.controller;

import com.course_aura_hub.common.dto.ApiResponse;
import com.course_aura_hub.student.dto.StudentCreateRequest;
import com.course_aura_hub.student.dto.StudentResponse;
import com.course_aura_hub.student.dto.StudentUpdateRequest;
import com.course_aura_hub.student.service.StudentService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/students")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Students", description = "Admin-only student management endpoints")
public class StudentController {

    private final StudentService studentService;

    @GetMapping
    @Operation(summary = "List students", description = "Returns students with optional status and search filters.")
    public ResponseEntity<ApiResponse<List<StudentResponse>>> findAll(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search
    ) {
        return ResponseEntity.ok(ApiResponse.success(studentService.findAll(status, search)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get student", description = "Returns a single student by id.")
    public ResponseEntity<ApiResponse<StudentResponse>> findById(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.success(studentService.findById(id)));
    }

    @PostMapping
    @Operation(summary = "Create student", description = "Creates a student record.")
    public ResponseEntity<ApiResponse<StudentResponse>> create(@Valid @RequestBody StudentCreateRequest request) {
        log.info("Received create student request");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Student created successfully", studentService.create(request)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update student", description = "Updates a student record.")
    public ResponseEntity<ApiResponse<StudentResponse>> update(
            @PathVariable Integer id,
            @Valid @RequestBody StudentUpdateRequest request
    ) {
        log.info("Received update student request id={}", id);
        return ResponseEntity.ok(ApiResponse.success(
                "Student updated successfully",
                studentService.update(id, request)
        ));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete student", description = "Deletes a student and their enrollment links.")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer id) {
        log.info("Received delete student request id={}", id);
        studentService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Student deleted successfully", null));
    }
}
