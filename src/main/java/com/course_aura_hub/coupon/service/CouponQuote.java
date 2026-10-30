package com.course_aura_hub.coupon.service;

import com.course_aura_hub.coupon.model.Coupon;
import com.course_aura_hub.course.model.Course;
import com.course_aura_hub.course.model.CoursePrice;

import java.math.BigDecimal;

/**
 * What a learner pays for a course.
 *
 * @param coupon         null when no code was applied
 * @param listAmount     the regular price
 * @param discountAmount everything taken off the regular price: the running sale plus the coupon
 */
public record CouponQuote(Coupon coupon, BigDecimal listAmount, BigDecimal discountAmount, BigDecimal finalAmount) {

    public static CouponQuote none(Course course) {
        CoursePrice current = course.currentPrice();
        BigDecimal list = course.getPrice();
        return new CouponQuote(null, list, list.subtract(current.price()), current.price());
    }
}
