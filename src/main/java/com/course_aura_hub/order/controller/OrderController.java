package com.course_aura_hub.order.controller;

import com.course_aura_hub.common.dto.ApiResponse;
import com.course_aura_hub.order.dto.AdminOrderPageResponse;
import com.course_aura_hub.order.dto.OrderCreateRequest;
import com.course_aura_hub.order.dto.OrderResponse;
import com.course_aura_hub.order.facade.CheckoutFacade;
import com.course_aura_hub.order.service.OrderService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Course checkout, order history and refunds")
public class OrderController {

    private final CheckoutFacade checkoutFacade;
    private final OrderService orderService;

    @PostMapping("/api/v1/me/orders")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Start checkout",
            description = "Opens an order for a paid course and returns where to pay. Enrollment happens once "
                    + "the provider confirms payment. Repeating the call reuses the open order."
    )
    public ResponseEntity<ApiResponse<OrderResponse>> checkout(
            @Valid @RequestBody OrderCreateRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                checkoutFacade.checkout(authentication.getName(), request.courseSlug())
        ));
    }

    @GetMapping("/api/v1/me/orders")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "My orders", description = "Newest first.")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> myOrders(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(orderService.findMine(authentication.getName())));
    }

    @GetMapping("/api/v1/me/orders/{orderNumber}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "My order", description = "Poll this after returning from the payment page.")
    public ResponseEntity<ApiResponse<OrderResponse>> myOrder(
            @PathVariable String orderNumber,
            Authentication authentication
    ) {
        return ResponseEntity.ok(ApiResponse.success(orderService.findMine(authentication.getName(), orderNumber)));
    }

    @PostMapping("/api/v1/me/orders/{orderNumber}/cancel")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Cancel my pending order")
    public ResponseEntity<ApiResponse<OrderResponse>> cancel(
            @PathVariable String orderNumber,
            Authentication authentication
    ) {
        return ResponseEntity.ok(ApiResponse.success(orderService.cancelMine(authentication.getName(), orderNumber)));
    }

    @PostMapping("/api/v1/payments/webhooks/{provider}")
    @Operation(summary = "Payment provider webhook", description = "Public. Authenticated by the provider's signature.")
    public ResponseEntity<ApiResponse<Void>> webhook(
            @PathVariable String provider,
            @RequestHeader(value = "X-Payment-Signature", required = false) String signature,
            @RequestBody String payload
    ) {
        checkoutFacade.handleWebhook(provider, signature, payload);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/api/v1/admin/orders")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List orders", description = "Admin-only. Paged, newest first, optionally by status.")
    public ResponseEntity<ApiResponse<AdminOrderPageResponse>> findAll(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(orderService.findAll(status, page, size)));
    }

    @PostMapping("/api/v1/admin/orders/{orderNumber}/refund")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Refund an order",
            description = "Admin-only. Refunds through the provider and removes the enrollment. "
                    + "Certificates already issued are kept."
    )
    public ResponseEntity<ApiResponse<OrderResponse>> refund(@PathVariable String orderNumber) {
        return ResponseEntity.ok(ApiResponse.success(checkoutFacade.refund(orderNumber)));
    }
}
