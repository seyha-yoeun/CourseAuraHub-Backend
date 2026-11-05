package com.course_aura_hub.refund.controller;

import com.course_aura_hub.auth.security.AuthUserPrincipal;
import com.course_aura_hub.common.dto.ApiResponse;
import com.course_aura_hub.refund.dto.RefundDtos;
import com.course_aura_hub.refund.facade.RefundRequestFacade;
import com.course_aura_hub.refund.service.RefundRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Refund requests", description = "Learners ask for refunds; the policy or an admin decides")
public class RefundRequestController {

    private final RefundRequestFacade facade;
    private final RefundRequestService service;

    @GetMapping("/api/v1/me/orders/{orderNumber}/refund-eligibility")
    @Operation(summary = "Can I get a refund?", description = "Whether a request is possible and whether it would be approved straight away.")
    public ResponseEntity<ApiResponse<RefundDtos.Eligibility>> eligibility(@PathVariable String orderNumber, Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(service.eligibility(authentication.getName(), orderNumber)));
    }

    @PostMapping("/api/v1/me/orders/{orderNumber}/refund-request")
    @Operation(
            summary = "Ask for a refund",
            description = "Inside the refund window and below the progress limit it is refunded at once; otherwise an admin decides."
    )
    public ResponseEntity<ApiResponse<RefundDtos.RefundRequestResponse>> request(
            @PathVariable String orderNumber,
            @Valid @RequestBody RefundDtos.CreateRequest request,
            Authentication authentication
    ) {
        RefundDtos.RefundRequestResponse response = facade.request(authentication.getName(), orderNumber, request.reason());
        String message = "approved".equals(response.status()) ? "Refunded" : "Refund requested; an admin will review it";
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(message, response));
    }

    @GetMapping("/api/v1/me/refund-requests")
    @Operation(summary = "My refund requests", description = "Newest first.")
    public ResponseEntity<ApiResponse<List<RefundDtos.RefundRequestResponse>>> mine(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(service.findMine(authentication.getName())));
    }

    @GetMapping("/api/v1/admin/refund-requests")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Refund requests", description = "Admin-only. status pending (oldest first, the default), approved, rejected or all.")
    public ResponseEntity<ApiResponse<RefundDtos.AdminPage>> all(
            @RequestParam(defaultValue = "pending") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(service.findForAdmin(status, page, size)));
    }

    @PostMapping("/api/v1/admin/refund-requests/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Approve a refund request", description = "Admin-only. Refunds the order through the normal refund.")
    public ResponseEntity<ApiResponse<RefundDtos.RefundRequestResponse>> approve(
            @PathVariable Integer id,
            @Valid @RequestBody(required = false) RefundDtos.DecisionRequest request,
            @AuthenticationPrincipal AuthUserPrincipal admin
    ) {
        return ResponseEntity.ok(ApiResponse.success("Refunded",
                facade.approve(id, admin.getUserId(), request == null ? null : request.note())));
    }

    @PostMapping("/api/v1/admin/refund-requests/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Decline a refund request", description = "Admin-only. The note is sent to the learner.")
    public ResponseEntity<ApiResponse<RefundDtos.RefundRequestResponse>> reject(
            @PathVariable Integer id,
            @Valid @RequestBody RefundDtos.DecisionRequest request,
            @AuthenticationPrincipal AuthUserPrincipal admin
    ) {
        return ResponseEntity.ok(ApiResponse.success("Refund declined", facade.reject(id, admin.getUserId(), request.note())));
    }
}
