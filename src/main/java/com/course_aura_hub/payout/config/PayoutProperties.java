package com.course_aura_hub.payout.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;

/**
 * How instructors are paid for courses they own.
 *
 * @param enabled                record earnings and accept payout requests at all
 * @param instructorSharePercent the instructor's share of a paid order when the course has no share of its own
 * @param holdDays               days after payment before a sale can be paid out; keep it at least the refund window
 * @param minimumAmount          smallest payout an instructor may request
 */
@ConfigurationProperties(prefix = "app.payouts")
public record PayoutProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("70") BigDecimal instructorSharePercent,
        @DefaultValue("14") int holdDays,
        @DefaultValue("10.00") BigDecimal minimumAmount
) {
}
