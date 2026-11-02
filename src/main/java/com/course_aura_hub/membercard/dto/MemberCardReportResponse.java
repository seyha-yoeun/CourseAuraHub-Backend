package com.course_aura_hub.membercard.dto;

import java.math.BigDecimal;

/** Totals for matching cash received against balances. Movements cover the last {@code days} days. */
public record MemberCardReportResponse(
        int days,
        String currency,
        long totalCards,
        long activeCards,
        BigDecimal outstandingBalance,
        long pendingApplications,
        long pendingTopUps,
        BigDecimal topUps,
        BigDecimal deductions,
        BigDecimal purchases,
        BigDecimal refunds
) {
}
