package com.course_aura_hub.cart.controller;

import com.course_aura_hub.cart.dto.CartCheckoutRequest;
import com.course_aura_hub.cart.dto.CartCouponPreviewResponse;
import com.course_aura_hub.cart.dto.CartCouponRequest;
import com.course_aura_hub.cart.dto.CartResponse;
import com.course_aura_hub.cart.dto.CheckoutResponse;
import com.course_aura_hub.cart.service.CartService;
import com.course_aura_hub.common.dto.ApiResponse;
import com.course_aura_hub.order.facade.CheckoutFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

@RestController
@RequestMapping("/api/v1/me/cart")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "Cart", description = "The caller's cart and checkout")
public class CartController {

    private final CartService cartService;
    private final CheckoutFacade checkoutFacade;

    @GetMapping
    @Operation(summary = "My cart", description = "Prices are today's; orders fix them at checkout.")
    public ResponseEntity<ApiResponse<CartResponse>> findMine(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(cartService.findMine(authentication.getName())));
    }

    @PutMapping("/{slug}")
    @Operation(summary = "Add a paid course to my cart")
    public ResponseEntity<ApiResponse<CartResponse>> add(@PathVariable String slug, Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(cartService.add(authentication.getName(), slug)));
    }

    @DeleteMapping("/{slug}")
    @Operation(summary = "Remove a course from my cart")
    public ResponseEntity<ApiResponse<CartResponse>> remove(@PathVariable String slug, Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(cartService.remove(authentication.getName(), slug)));
    }

    @PostMapping("/coupon-preview")
    @Operation(summary = "Preview a coupon", description = "A code is used once per learner, so it applies to one course: "
            + "the course it is limited to, otherwise the most expensive one.")
    public ResponseEntity<ApiResponse<CartCouponPreviewResponse>> previewCoupon(
            @Valid @RequestBody CartCouponRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(ApiResponse.success(cartService.previewCoupon(authentication.getName(), request.code())));
    }

    @PostMapping("/checkout")
    @Operation(
            summary = "Check out the cart",
            description = "Opens one order per course under a single payment. Courses leave the cart once they are paid for."
    )
    public ResponseEntity<ApiResponse<CheckoutResponse>> checkout(
            @RequestBody(required = false) CartCheckoutRequest request,
            Authentication authentication
    ) {
        String couponCode = request == null ? null : request.couponCode();
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                checkoutFacade.checkoutCart(authentication.getName(), couponCode)
        ));
    }
}
