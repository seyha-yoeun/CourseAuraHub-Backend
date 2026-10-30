package com.course_aura_hub.coupon.controller;

import com.course_aura_hub.common.dto.ApiResponse;
import com.course_aura_hub.coupon.dto.CouponCreateRequest;
import com.course_aura_hub.coupon.dto.CouponPreviewRequest;
import com.course_aura_hub.coupon.dto.CouponPreviewResponse;
import com.course_aura_hub.coupon.dto.CouponResponse;
import com.course_aura_hub.coupon.dto.CouponUpdateRequest;
import com.course_aura_hub.coupon.service.CouponService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Coupons", description = "Discount codes")
public class CouponController {

    private final CouponService couponService;

    @PostMapping("/api/v1/me/coupons/preview")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Preview a coupon", description = "Shows the discounted price. Does not reserve a use.")
    public ResponseEntity<ApiResponse<CouponPreviewResponse>> preview(
            @Valid @RequestBody CouponPreviewRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(ApiResponse.success(couponService.preview(authentication.getName(), request)));
    }

    @GetMapping("/api/v1/admin/coupons")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List coupons", description = "Admin-only. Newest first, with paid redemption counts.")
    public ResponseEntity<ApiResponse<List<CouponResponse>>> findAll() {
        return ResponseEntity.ok(ApiResponse.success(couponService.findAll()));
    }

    @PostMapping("/api/v1/admin/coupons")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create coupon", description = "Admin-only.")
    public ResponseEntity<ApiResponse<CouponResponse>> create(@Valid @RequestBody CouponCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(couponService.create(request)));
    }

    @PatchMapping("/api/v1/admin/coupons/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update coupon", description = "Admin-only. Deactivate instead of deleting, to keep order history.")
    public ResponseEntity<ApiResponse<CouponResponse>> update(
            @PathVariable Integer id,
            @Valid @RequestBody CouponUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(couponService.update(id, request)));
    }
}
