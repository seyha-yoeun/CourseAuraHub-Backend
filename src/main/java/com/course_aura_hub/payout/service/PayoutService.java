package com.course_aura_hub.payout.service;

import com.course_aura_hub.common.security.Actor;
import com.course_aura_hub.payout.dto.PayoutDtos;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Instructor earnings, payout requests and the admin review that pays them. */
public interface PayoutService {

    /** @param status pending, available, paid, reversed, or blank for every row */
    PayoutDtos.EarningsPage findEarnings(Actor actor, String status, int page, int size);

    /** Claims every available earning in one payout request. */
    PayoutDtos.PayoutResponse requestPayout(Actor actor, PayoutDtos.CreatePayoutRequest request);

    List<PayoutDtos.PayoutResponse> findMyPayouts(Actor actor);

    /** @param status requested (the default), approved, rejected, paid or all */
    PayoutDtos.AdminPage findForAdmin(String status, int page, int size);

    PayoutDtos.PayoutResponse approve(Integer id, UUID adminId, String note);

    /** Releases the claimed earnings, so the next request includes them again. */
    PayoutDtos.PayoutResponse reject(Integer id, UUID adminId, String note);

    /** Records that the money was sent; only an approved payout can be marked paid. */
    PayoutDtos.PayoutResponse markPaid(Integer id, UUID adminId, String paymentReference, String note);

    PayoutDtos.RevenueShareResponse findRevenueShare(String courseSlug);

    /** @param instructorPercent null to use the platform default again */
    PayoutDtos.RevenueShareResponse setRevenueShare(String courseSlug, BigDecimal instructorPercent);
}
