package com.course_aura_hub.payout.service;

/** Records instructor earnings as orders are paid and refunded. */
public interface EarningService {

    /** Makes held sales payable once their hold has passed. @return how many were released */
    int releaseDue();
}
