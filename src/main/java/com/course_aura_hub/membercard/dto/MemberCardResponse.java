package com.course_aura_hub.membercard.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record MemberCardResponse(
        Integer id,
        Integer studentId,
        String studentEmail,
        String cardNumber,
        String holderName,
        BigDecimal balance,
        String currency,
        String status,
        Instant createdAt
) {
}
