package com.course_aura_hub.subscription.controller;

import com.course_aura_hub.common.dto.ApiResponse;
import com.course_aura_hub.subscription.dto.SubscriptionDtos;
import com.course_aura_hub.subscription.facade.SubscriptionFacade;
import com.course_aura_hub.subscription.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
@Tag(name = "Subscriptions", description = "All-access plans: subscribe, enroll in any course, renew or cancel")
public class SubscriptionController {

    private static final String ADMIN = "hasRole('ADMIN')";

    private final SubscriptionService subscriptionService;
    private final SubscriptionFacade subscriptionFacade;

    @GetMapping("/api/v1/subscription-plans")
    @Operation(summary = "Subscription plans", description = "Public. Active plans, cheapest first.")
    public ResponseEntity<ApiResponse<List<SubscriptionDtos.Plan>>> plans() {
        return ResponseEntity.ok(ApiResponse.success(subscriptionService.activePlans()));
    }

    @PostMapping("/api/v1/me/subscription")
    @Operation(summary = "Subscribe", description = "paymentMethod member_card charges the card at once; online "
            + "returns a checkoutUrl. One subscription at a time; an unpaid sign-up is replaced.")
    public ResponseEntity<ApiResponse<SubscriptionDtos.MySubscription>> subscribe(
            @Valid @RequestBody SubscriptionDtos.SubscribeRequest request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Subscription started",
                subscriptionFacade.subscribe(authentication.getName(), request.planCode(), request.paymentMethod())));
    }

    @GetMapping("/api/v1/me/subscription")
    @Operation(summary = "My subscription", description = "The latest one with recent payments, or null.")
    public ResponseEntity<ApiResponse<SubscriptionDtos.MySubscription>> mine(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(subscriptionService.mine(authentication.getName())));
    }

    @PostMapping("/api/v1/me/subscription/pay")
    @Operation(summary = "Pay what my subscription owes", description = "For a renewal that is due, for example "
            + "after topping up a member card.")
    public ResponseEntity<ApiResponse<SubscriptionDtos.MySubscription>> pay(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(subscriptionFacade.payOpen(authentication.getName())));
    }

    @PostMapping("/api/v1/me/subscription/cancel")
    @Operation(summary = "Cancel my subscription", description = "Access continues until the period ends; an unpaid "
            + "sign-up is cancelled at once.")
    public ResponseEntity<ApiResponse<SubscriptionDtos.MySubscription>> cancel(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Subscription cancelled", subscriptionService.cancel(authentication.getName())));
    }

    @PostMapping("/api/v1/me/subscription/resume")
    @Operation(summary = "Keep my subscription", description = "Undoes a cancellation before the period ends.")
    public ResponseEntity<ApiResponse<SubscriptionDtos.MySubscription>> resume(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Subscription resumed", subscriptionService.resume(authentication.getName())));
    }

    @PostMapping("/api/v1/me/subscription/enrollments")
    @Operation(summary = "Enroll through my subscription", description = "Any published course, free while the "
            + "subscription lasts. Ending it removes these enrollments; progress is kept for a return.")
    public ResponseEntity<ApiResponse<Void>> enroll(@Valid @RequestBody SubscriptionDtos.EnrollRequest request,
                                                    Authentication authentication) {
        subscriptionService.enroll(authentication.getName(), request.courseSlug());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Enrolled", null));
    }

    @GetMapping("/api/v1/admin/subscription-plans")
    @PreAuthorize(ADMIN)
    @Operation(summary = "All plans", description = "Admin-only, including inactive ones.")
    public ResponseEntity<ApiResponse<List<SubscriptionDtos.Plan>>> allPlans() {
        return ResponseEntity.ok(ApiResponse.success(subscriptionService.allPlans()));
    }

    @PostMapping("/api/v1/admin/subscription-plans")
    @PreAuthorize(ADMIN)
    @Operation(summary = "Create a plan", description = "Admin-only. billingInterval month or year.")
    public ResponseEntity<ApiResponse<SubscriptionDtos.Plan>> createPlan(@Valid @RequestBody SubscriptionDtos.PlanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Plan created",
                subscriptionService.createPlan(request)));
    }

    @PutMapping("/api/v1/admin/subscription-plans/{id}")
    @PreAuthorize(ADMIN)
    @Operation(summary = "Change a plan", description = "Admin-only. Deactivate instead of deleting; a new price "
            + "applies from each subscriber's next renewal.")
    public ResponseEntity<ApiResponse<SubscriptionDtos.Plan>> updatePlan(@PathVariable Integer id,
                                                                         @Valid @RequestBody SubscriptionDtos.PlanRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Plan saved", subscriptionService.updatePlan(id, request)));
    }

    @GetMapping("/api/v1/admin/subscriptions")
    @PreAuthorize(ADMIN)
    @Operation(summary = "Subscriptions", description = "Admin-only. status pending, active, past_due, cancelled, expired or all.")
    public ResponseEntity<ApiResponse<SubscriptionDtos.AdminPage>> all(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(subscriptionService.findForAdmin(status, page, size)));
    }
}
