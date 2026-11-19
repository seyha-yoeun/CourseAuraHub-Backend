package com.course_aura_hub.payout.controller;

import com.course_aura_hub.auth.security.AuthUserPrincipal;
import com.course_aura_hub.common.dto.ApiResponse;
import com.course_aura_hub.common.security.Actor;
import com.course_aura_hub.payout.dto.PayoutDtos;
import com.course_aura_hub.payout.service.PayoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
@Tag(name = "Payouts", description = "Instructor earnings from course sales and the payouts that pay them")
public class PayoutController {

    private static final String STAFF = "hasAnyRole('ADMIN', 'INSTRUCTOR')";
    private static final String ADMIN = "hasRole('ADMIN')";

    private final PayoutService payoutService;

    @GetMapping("/api/v1/instructor/earnings")
    @PreAuthorize(STAFF)
    @Operation(summary = "My earnings", description = "Totals and every sale on courses I own, newest first. "
            + "status filters by pending, available, paid or reversed. Learners are never identified.")
    public ResponseEntity<ApiResponse<PayoutDtos.EarningsPage>> earnings(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal AuthUserPrincipal user
    ) {
        return ResponseEntity.ok(ApiResponse.success(payoutService.findEarnings(Actor.of(user), status, page, size)));
    }

    @PostMapping("/api/v1/instructor/payouts")
    @PreAuthorize(STAFF)
    @Operation(summary = "Request a payout", description = "Claims everything available. One payout at a time, "
            + "above the minimum. An admin approves it and then records the transfer.")
    public ResponseEntity<ApiResponse<PayoutDtos.PayoutResponse>> request(
            @Valid @RequestBody PayoutDtos.CreatePayoutRequest request,
            @AuthenticationPrincipal AuthUserPrincipal user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Payout requested",
                payoutService.requestPayout(Actor.of(user), request)));
    }

    @GetMapping("/api/v1/instructor/payouts")
    @PreAuthorize(STAFF)
    @Operation(summary = "My payouts", description = "Newest first.")
    public ResponseEntity<ApiResponse<List<PayoutDtos.PayoutResponse>>> myPayouts(
            @AuthenticationPrincipal AuthUserPrincipal user
    ) {
        return ResponseEntity.ok(ApiResponse.success(payoutService.findMyPayouts(Actor.of(user))));
    }

    @GetMapping("/api/v1/admin/payouts")
    @PreAuthorize(ADMIN)
    @Operation(summary = "Payout requests", description = "Admin-only. status requested (oldest first, the default), "
            + "approved, rejected, paid or all.")
    public ResponseEntity<ApiResponse<PayoutDtos.AdminPage>> all(
            @RequestParam(defaultValue = "requested") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(payoutService.findForAdmin(status, page, size)));
    }

    @PostMapping("/api/v1/admin/payouts/{id}/approve")
    @PreAuthorize(ADMIN)
    @Operation(summary = "Approve a payout", description = "Admin-only. Not your own.")
    public ResponseEntity<ApiResponse<PayoutDtos.PayoutResponse>> approve(
            @PathVariable Integer id,
            @Valid @RequestBody(required = false) PayoutDtos.DecisionRequest request,
            @AuthenticationPrincipal AuthUserPrincipal admin
    ) {
        return ResponseEntity.ok(ApiResponse.success("Payout approved",
                payoutService.approve(id, admin.getUserId(), request == null ? null : request.note())));
    }

    @PostMapping("/api/v1/admin/payouts/{id}/reject")
    @PreAuthorize(ADMIN)
    @Operation(summary = "Decline a payout", description = "Admin-only. The note is sent to the instructor, and the "
            + "earnings can be requested again.")
    public ResponseEntity<ApiResponse<PayoutDtos.PayoutResponse>> reject(
            @PathVariable Integer id,
            @Valid @RequestBody PayoutDtos.DecisionRequest request,
            @AuthenticationPrincipal AuthUserPrincipal admin
    ) {
        return ResponseEntity.ok(ApiResponse.success("Payout declined",
                payoutService.reject(id, admin.getUserId(), request.note())));
    }

    @PostMapping("/api/v1/admin/payouts/{id}/mark-paid")
    @PreAuthorize(ADMIN)
    @Operation(summary = "Mark a payout paid", description = "Admin-only. Record the transfer after sending the money.")
    public ResponseEntity<ApiResponse<PayoutDtos.PayoutResponse>> markPaid(
            @PathVariable Integer id,
            @Valid @RequestBody(required = false) PayoutDtos.MarkPaidRequest request,
            @AuthenticationPrincipal AuthUserPrincipal admin
    ) {
        return ResponseEntity.ok(ApiResponse.success("Payout marked paid", payoutService.markPaid(id, admin.getUserId(),
                request == null ? null : request.paymentReference(), request == null ? null : request.note())));
    }

    @GetMapping("/api/v1/admin/courses/{slug}/revenue-share")
    @PreAuthorize(ADMIN)
    @Operation(summary = "A course's instructor share", description = "Admin-only.")
    public ResponseEntity<ApiResponse<PayoutDtos.RevenueShareResponse>> revenueShare(@PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.success(payoutService.findRevenueShare(slug)));
    }

    @PutMapping("/api/v1/admin/courses/{slug}/revenue-share")
    @PreAuthorize(ADMIN)
    @Operation(summary = "Set a course's instructor share", description = "Admin-only. A null percent goes back to "
            + "the platform default. Only future sales use it.")
    public ResponseEntity<ApiResponse<PayoutDtos.RevenueShareResponse>> setRevenueShare(
            @PathVariable String slug,
            @Valid @RequestBody PayoutDtos.RevenueShareRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Revenue share saved",
                payoutService.setRevenueShare(slug, request.instructorPercent())));
    }
}
