package com.course_aura_hub.membercard.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record MemberCardTopUpCreateRequest(

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
        @Digits(integer = 8, fraction = 2, message = "Amount must have at most 8 digits and 2 decimals")
        BigDecimal amount,

        @Size(max = 500, message = "Note must not exceed 500 characters")
        String note,

        // The storage:// url from a finished TOP_UP_RECEIPT upload
        @Size(max = 500, message = "Receipt link is too long")
        String receiptUrl,

        @Size(max = 255, message = "Receipt name is too long")
        String receiptName
) {
}
