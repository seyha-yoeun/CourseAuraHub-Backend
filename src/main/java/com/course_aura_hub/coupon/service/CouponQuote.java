package com.course_aura_hub.coupon.service;

import com.course_aura_hub.coupon.model.Coupon;

import java.math.BigDecimal;

/**
 * What a learner pays for a course.
 *
 * @param coupon null when no code was applied
 */
public record CouponQuote(Coupon coupon, BigDecimal listAmount, BigDecimal discountAmount, BigDecimal finalAmount) {

    public static CouponQuote none(BigDecimal price) {
        return new CouponQuote(null, price, BigDecimal.ZERO, price);
    }
}
