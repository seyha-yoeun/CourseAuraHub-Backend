package com.course_aura_hub.refund.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * When a learner's refund request is approved without an admin.
 *
 * @param enabled            learners can ask for refunds at all
 * @param windowDays         days after payment in which a request qualifies
 * @param maxProgressPercent most of the course a learner may have completed and still qualify
 * @param autoApprove        refund qualifying requests straight away; otherwise every request waits for an admin
 */
@ConfigurationProperties(prefix = "app.refunds")
public record RefundPolicyProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("14") int windowDays,
        @DefaultValue("30") int maxProgressPercent,
        @DefaultValue("true") boolean autoApprove
) {
}
