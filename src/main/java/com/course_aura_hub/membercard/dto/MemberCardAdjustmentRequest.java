package com.course_aura_hub.membercard.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record MemberCardAdjustmentRequest(

        @NotBlank(message = "Adjustment type is required")
        String type,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
        @Digits(integer = 8, fraction = 2, message = "Amount must have at most 8 digits and 2 decimals")
        BigDecimal amount,

        @NotBlank(message = "Say why the balance is changing")
        @Size(max = 500, message = "Note must not exceed 500 characters")
        String note,

        // Sent again on a retry, so a repeated request changes the balance only once
        @NotBlank(message = "Idempotency key is required")
        @Size(max = 100, message = "Idempotency key must not exceed 100 characters")
        String idempotencyKey
) {
}
