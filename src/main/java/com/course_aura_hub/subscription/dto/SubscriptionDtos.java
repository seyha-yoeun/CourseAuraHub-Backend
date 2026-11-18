package com.course_aura_hub.subscription.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Subscription plans, a learner's subscription and its payments. */
public final class SubscriptionDtos {

    private SubscriptionDtos() {
    }

    public record PlanRequest(
            @NotBlank(message = "Code is required")
            @Pattern(regexp = "[a-z0-9-]{2,40}", message = "Code is 2-40 lowercase letters, digits or dashes")
            String code,
            @NotBlank(message = "Name is required") @Size(max = 100) String name,
            @Size(max = 1000) String description,
            @NotNull(message = "Price is required")
            @DecimalMin(value = "0.01", message = "Price must be above zero")
            @Digits(integer = 8, fraction = 2) BigDecimal price,
            @NotBlank(message = "Choose month or year") String billingInterval,
            Boolean active
    ) {
    }

    public record Plan(Integer id, String code, String name, String description, BigDecimal price, String currency,
                       String billingInterval, boolean active) {
    }

    /** @param paymentMethod online (the payment provider) or member_card */
    public record SubscribeRequest(@NotBlank(message = "Choose a plan") String planCode, String paymentMethod) {
    }

    public record EnrollRequest(@NotBlank(message = "courseSlug is required") String courseSlug) {
    }

    public record Payment(String paymentNumber, BigDecimal amount, String currency, String status, Instant periodStart,
                          Instant periodEnd, String checkoutUrl, Instant createdAt, Instant paidAt) {
    }

    /**
     * @param hasAccess  whether it currently lets me enroll in and study any course
     * @param checkoutUrl where to pay an open payment, if any
     */
    public record MySubscription(Integer id, Plan plan, String status, boolean hasAccess, Instant currentPeriodStart,
                                 Instant currentPeriodEnd, boolean cancelAtPeriodEnd, String paymentMethod,
                                 String checkoutUrl, int courses, List<Payment> payments) {
    }

    public record AdminSubscription(Integer id, Integer studentId, String learnerName, String learnerEmail, String planCode,
                                    String status, Instant currentPeriodEnd, boolean cancelAtPeriodEnd, Instant createdAt) {
    }

    public record AdminPage(List<AdminSubscription> items, int page, int size, long totalElements, int totalPages) {
    }

    /** What the facade needs to start a payment outside the transaction. */
    public record OpenPayment(Integer subscriptionId, String paymentNumber, BigDecimal amount, String currency,
                              String provider) {
    }
}
