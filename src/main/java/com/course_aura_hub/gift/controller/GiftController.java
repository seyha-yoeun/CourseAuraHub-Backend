package com.course_aura_hub.gift.controller;

import com.course_aura_hub.auth.security.AuthUserPrincipal;
import com.course_aura_hub.common.dto.ApiResponse;
import com.course_aura_hub.gift.dto.GiftDtos;
import com.course_aura_hub.gift.service.GiftService;
import com.course_aura_hub.order.dto.OrderResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Gifts and access codes", description = "Buy a course for someone, redeem codes, and issue code batches")
public class GiftController {

    private final GiftService giftService;

    @PostMapping("/api/v1/me/gifts")
    @Operation(
            summary = "Give a course",
            description = "Starts a checkout like a normal purchase (online or member card). Once paid, the recipient gets a code by email."
    )
    public ResponseEntity<ApiResponse<OrderResponse>> give(@Valid @RequestBody GiftDtos.GiftRequest request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Gift checkout started", giftService.sendGift(authentication.getName(), request)));
    }

    @GetMapping("/api/v1/me/gifts")
    @Operation(summary = "Gifts I sent", description = "With whether each was paid and redeemed.")
    public ResponseEntity<ApiResponse<List<GiftDtos.SentGift>>> sent(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(giftService.findSent(authentication.getName())));
    }

    @PostMapping("/api/v1/me/access-codes/redeem")
    @Operation(summary = "Redeem a code", description = "Enrolls me in the code's course for free.")
    public ResponseEntity<ApiResponse<GiftDtos.Redeemed>> redeem(@Valid @RequestBody GiftDtos.RedeemRequest request, Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Course unlocked", giftService.redeem(authentication.getName(), request.code())));
    }

    @PostMapping("/api/v1/admin/access-codes")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Issue access codes", description = "Admin-only. Up to 500 per batch. The full codes are returned only in this response.")
    public ResponseEntity<ApiResponse<GiftDtos.IssuedBatch>> issue(
            @Valid @RequestBody GiftDtos.IssueRequest request, @AuthenticationPrincipal AuthUserPrincipal admin) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Codes issued", giftService.issue(admin.getUserId(), request)));
    }

    @GetMapping("/api/v1/admin/access-codes")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Access codes", description = "Admin-only. One batch, or every admin-issued code. Shows the last characters only.")
    public ResponseEntity<ApiResponse<List<GiftDtos.Code>>> codes(@RequestParam(required = false) String batch) {
        return ResponseEntity.ok(ApiResponse.success(giftService.findCodes(batch)));
    }

    @DeleteMapping("/api/v1/admin/access-codes/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Revoke a code", description = "Admin-only. Enrollments it already made are kept.")
    public ResponseEntity<ApiResponse<GiftDtos.Code>> revoke(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.success("Code revoked", giftService.revoke(id)));
    }
}
