package com.course_aura_hub.payout.dto;

import com.course_aura_hub.payout.model.InstructorEarning;
import com.course_aura_hub.payout.model.PayoutRequest;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/** Request and response shapes for instructor earnings and payouts. Learner identities never appear here. */
public final class PayoutDtos {

    private PayoutDtos() {
    }

    public record CreatePayoutRequest(
            @NotBlank(message = "Choose how you want to be paid")
            @Size(max = 40, message = "Method must not exceed 40 characters")
            String method,
            @NotBlank(message = "Enter the account to pay")
            @Size(max = 500, message = "Account details must not exceed 500 characters")
            String accountDetails,
            @Size(max = 1000, message = "Note must not exceed 1000 characters")
            String note
    ) {
    }

    public record DecisionRequest(@Size(max = 1000, message = "Note must not exceed 1000 characters") String note) {
    }

    public record MarkPaidRequest(
            @Size(max = 200, message = "Reference must not exceed 200 characters") String paymentReference,
            @Size(max = 1000, message = "Note must not exceed 1000 characters") String note
    ) {
    }

    /** A null percent removes the course's own share, so it falls back to the platform default. */
    public record RevenueShareRequest(
            @DecimalMin(value = "0", message = "Share cannot be negative")
            @DecimalMax(value = "100", message = "Share cannot exceed 100")
            @Digits(integer = 3, fraction = 2, message = "Share allows at most 2 decimals")
            BigDecimal instructorPercent
    ) {
    }

    /** @param custom whether the course has its own share instead of the platform default */
    public record RevenueShareResponse(String courseSlug, BigDecimal instructorPercent, boolean custom,
                                       BigDecimal platformDefaultPercent) {
    }

    public record EarningResponse(
            Integer id,
            String orderNumber,
            String courseSlug,
            String courseTitle,
            String type,
            BigDecimal gross,
            BigDecimal platformFee,
            BigDecimal net,
            BigDecimal sharePercent,
            String currency,
            String status,
            Instant availableAt,
            Integer payoutId,
            Instant createdAt
    ) {

        public static EarningResponse from(InstructorEarning earning) {
            return new EarningResponse(
                    earning.getId(),
                    earning.getOrder().getOrderNumber(),
                    earning.getCourse().getSlug(),
                    earning.getCourse().getTitle(),
                    lower(earning.getType().name()),
                    earning.getGross(),
                    earning.getPlatformFee(),
                    earning.getNet(),
                    earning.getSharePercent(),
                    earning.getCurrency(),
                    lower(earning.getStatus().name()),
                    earning.getAvailableAt(),
                    earning.getPayoutId(),
                    earning.getCreatedAt()
            );
        }
    }

    /**
     * @param pending    sales still inside the hold
     * @param available  money that can be requested now
     * @param inPayout   money claimed by a payout that hasn't been paid yet
     * @param paidOut    everything paid so far
     * @param canRequest whether a payout request would be accepted right now
     */
    public record EarningsSummary(String currency, BigDecimal pending, BigDecimal available, BigDecimal inPayout,
                                  BigDecimal paidOut, BigDecimal minimumPayout, boolean canRequest) {
    }

    public record EarningsPage(EarningsSummary summary, List<EarningResponse> items, int page, int size,
                               long totalElements, int totalPages) {
    }

    public record PayoutResponse(
            Integer id,
            BigDecimal amount,
            String currency,
            String method,
            String accountDetails,
            String note,
            String status,
            String decisionNote,
            String paymentReference,
            Instant createdAt,
            Instant reviewedAt,
            Instant paidAt
    ) {

        public static PayoutResponse from(PayoutRequest payout) {
            return new PayoutResponse(
                    payout.getId(),
                    payout.getAmount(),
                    payout.getCurrency(),
                    payout.getMethod(),
                    payout.getAccountDetails(),
                    payout.getNote(),
                    lower(payout.getStatus().name()),
                    payout.getDecisionNote(),
                    payout.getPaymentReference(),
                    payout.getCreatedAt(),
                    payout.getReviewedAt(),
                    payout.getPaidAt()
            );
        }
    }

    /** Admin view: who is asking. The instructor's own contact details, never a learner's. */
    public record AdminPayoutResponse(PayoutResponse payout, String instructorId, String instructorName,
                                      String instructorEmail, int earnings) {
    }

    public record AdminPage(List<AdminPayoutResponse> items, int page, int size, long totalElements, int totalPages) {
    }

    private static String lower(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
