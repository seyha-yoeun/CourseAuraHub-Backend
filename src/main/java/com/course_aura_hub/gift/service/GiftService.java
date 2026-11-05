package com.course_aura_hub.gift.service;

import com.course_aura_hub.gift.dto.GiftDtos;
import com.course_aura_hub.order.dto.OrderResponse;

import java.util.List;
import java.util.UUID;

public interface GiftService {

    /** Buys a course for someone; once paid, they get a code by email. */
    OrderResponse sendGift(String email, GiftDtos.GiftRequest request);

    List<GiftDtos.SentGift> findSent(String email);

    GiftDtos.Redeemed redeem(String email, String code);

    GiftDtos.IssuedBatch issue(UUID adminId, GiftDtos.IssueRequest request);

    List<GiftDtos.Code> findCodes(String batch);

    GiftDtos.Code revoke(Integer id);
}
