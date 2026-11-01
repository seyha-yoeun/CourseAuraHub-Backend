package com.course_aura_hub.membercard.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record MemberCardTransactionResponse(
        Long id,
        String type,
        BigDecimal amount,
        BigDecimal balanceAfter,
        String reference,
        String note,
        Instant createdAt
) {
}
