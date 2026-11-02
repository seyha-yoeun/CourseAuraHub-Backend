package com.course_aura_hub.membercard.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record MemberCardTopUpResponse(
        Integer id,
        Integer cardId,
        String cardNumber,
        String holderName,
        String studentEmail,
        BigDecimal amount,
        String currency,
        String note,
        // A short-lived signed link
        String receiptUrl,
        String receiptName,
        String status,
        String reviewNote,
        Instant createdAt,
        Instant reviewedAt
) {
}
