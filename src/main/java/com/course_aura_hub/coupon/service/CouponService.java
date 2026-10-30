package com.course_aura_hub.coupon.service;

import com.course_aura_hub.coupon.dto.CouponCreateRequest;
import com.course_aura_hub.coupon.dto.CouponPreviewRequest;
import com.course_aura_hub.coupon.dto.CouponPreviewResponse;
import com.course_aura_hub.coupon.dto.CouponResponse;
import com.course_aura_hub.coupon.dto.CouponUpdateRequest;
import com.course_aura_hub.course.model.Course;

import java.util.List;

public interface CouponService {

    List<CouponResponse> findAll();

    CouponResponse create(CouponCreateRequest request);

    CouponResponse update(Integer id, CouponUpdateRequest request);

    CouponPreviewResponse preview(String email, CouponPreviewRequest request);

    /**
     * Locks the coupon and prices the course, so parallel checkouts can't overrun a limit.
     * Must run in the transaction that saves the order.
     *
     * @param studentId      the buyer
     * @param excludeOrderId the buyer's open order for this course, which is about to be reused or replaced
     */
    CouponQuote applyToOrder(String code, Course course, Integer studentId, Integer excludeOrderId);
}
