package com.course_aura_hub.course.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

public enum DiscountType {
    PERCENT,
    FIXED;

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /** The amount taken off the price, which never exceeds the price itself. */
    public BigDecimal amountOff(BigDecimal price, BigDecimal value) {
        BigDecimal amount = this == PERCENT
                ? price.multiply(value).divide(HUNDRED, 2, RoundingMode.HALF_UP)
                : value;
        return amount.min(price);
    }
}
