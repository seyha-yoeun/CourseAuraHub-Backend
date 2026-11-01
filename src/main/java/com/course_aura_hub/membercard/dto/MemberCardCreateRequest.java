package com.course_aura_hub.membercard.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record MemberCardCreateRequest(

        @NotNull(message = "Learner is required")
        Integer studentId,

        @NotBlank(message = "Holder name is required")
        @Size(max = 150, message = "Holder name must not exceed 150 characters")
        String holderName,

        // Optional; recorded in the ledger as the card's first top-up
        @DecimalMin(value = "0.00", message = "Opening balance can't be negative")
        @Digits(integer = 8, fraction = 2, message = "Opening balance must have at most 8 digits and 2 decimals")
        BigDecimal openingBalance
) {
}
