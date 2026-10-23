package com.course_aura_hub.common.util;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pure calculation helpers for analytics responses.
 */
@Component
public class AnalyticsCalculator {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    public BigDecimal percentageChange(BigDecimal current, BigDecimal previous) {
        if (current == null || previous == null || previous.signum() == 0) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }

        return current.subtract(previous)
                .divide(previous, 6, RoundingMode.HALF_UP)
                .multiply(ONE_HUNDRED)
                .setScale(1, RoundingMode.HALF_UP);
    }

    public BigDecimal percentageChange(long current, long previous) {
        return percentageChange(BigDecimal.valueOf(current), BigDecimal.valueOf(previous));
    }

    public BigDecimal average(BigDecimal total, long count, int scale) {
        if (total == null || count <= 0) {
            return BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP);
        }
        return total.divide(BigDecimal.valueOf(count), scale, RoundingMode.HALF_UP);
    }
}
