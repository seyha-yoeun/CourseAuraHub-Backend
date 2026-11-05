package com.course_aura_hub.refund.dto;

import com.course_aura_hub.refund.model.RefundRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Request and response shapes for refund requests. */
public final class RefundDtos {

    private RefundDtos() {
    }

    public record CreateRequest(
            @NotBlank(message = "Tell us why you want a refund")
            @Size(max = 1000, message = "Reason must not exceed 1000 characters")
            String reason
    ) {
    }

    public record DecisionRequest(@Size(max = 1000, message = "Note must not exceed 1000 characters") String note) {
    }

    /**
     * Whether a paid order can still be refunded and whether it would be approved straight away.
     *
     * @param reasons why it would wait for an admin, or why it can't be requested at all
     */
    public record Eligibility(boolean canRequest, boolean automatic, int progressPercent, Instant windowEndsAt,
                              List<String> reasons) {
    }

    /** Learner-facing view of a request. */
    public record RefundRequestResponse(
            Integer id,
            String orderNumber,
            String courseSlug,
            String courseTitle,
            BigDecimal amount,
            String currency,
            String reason,
            String status,
            boolean autoApproved,
            int progressPercent,
            String decisionNote,
            Instant createdAt,
            Instant reviewedAt
    ) {

        public static RefundRequestResponse from(RefundRequest request) {
            return new RefundRequestResponse(
                    request.getId(),
                    request.getOrder().getOrderNumber(),
                    request.getOrder().getCourse().getSlug(),
                    request.getOrder().getCourse().getTitle(),
                    request.getOrder().getAmount(),
                    request.getOrder().getCurrency(),
                    request.getReason(),
                    request.getStatus().name().toLowerCase(java.util.Locale.ROOT),
                    request.isAutoApproved(),
                    request.getProgressPercent(),
                    request.getDecisionNote(),
                    request.getCreatedAt(),
                    request.getReviewedAt()
            );
        }
    }

    /** Admin view: the learner's name and email are admin-only. */
    public record AdminRefundRequestResponse(RefundRequestResponse request, String studentName, String studentEmail,
                                             String provider, Instant paidAt) {
    }

    public record AdminPage(List<AdminRefundRequestResponse> items, int page, int size, long totalElements, int totalPages) {
    }
}
