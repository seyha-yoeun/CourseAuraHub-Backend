package com.course_aura_hub.course.controller;

import com.course_aura_hub.common.dto.ApiResponse;
import com.course_aura_hub.course.dto.CourseDiscountCreateRequest;
import com.course_aura_hub.course.dto.CourseDiscountResponse;
import com.course_aura_hub.course.dto.CourseDiscountUpdateRequest;
import com.course_aura_hub.course.service.CourseDiscountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/discounts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Course discounts", description = "Scheduled sale prices, applied without a code")
public class CourseDiscountController {

    private final CourseDiscountService discountService;

    @GetMapping
    @Operation(summary = "List discounts", description = "Admin-only. Newest first; filter with courseSlug.")
    public ResponseEntity<ApiResponse<List<CourseDiscountResponse>>> findAll(
            @RequestParam(required = false) String courseSlug
    ) {
        return ResponseEntity.ok(ApiResponse.success(discountService.findAll(courseSlug)));
    }

    @PostMapping
    @Operation(summary = "Create discount", description = "Admin-only. A course can't have two discounts in the same period.")
    public ResponseEntity<ApiResponse<CourseDiscountResponse>> create(
            @Valid @RequestBody CourseDiscountCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(discountService.create(request)));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update discount", description = "Admin-only. Set active to false to end a sale early.")
    public ResponseEntity<ApiResponse<CourseDiscountResponse>> update(
            @PathVariable Integer id,
            @Valid @RequestBody CourseDiscountUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(discountService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete discount", description = "Admin-only. Existing orders keep the price they were placed at.")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        discountService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
