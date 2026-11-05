package com.course_aura_hub.refund.service;

import com.course_aura_hub.refund.dto.RefundDtos;

import java.util.List;
import java.util.UUID;

/** The transactional steps of a refund request; {@code RefundRequestFacade} runs the refund itself between them. */
public interface RefundRequestService {

    /** A created request, and whether the policy approves it without an admin. */
    record Created(Integer id, String orderNumber, boolean automatic) {
    }

    RefundDtos.Eligibility eligibility(String email, String orderNumber);

    Created create(String email, String orderNumber, String reason);

    /** The order number of a request that is still waiting, locked for the decision. */
    String requirePending(Integer id);

    RefundDtos.RefundRequestResponse markApproved(Integer id, UUID reviewer, String note, boolean automatic);

    /** An automatic refund that couldn't run stays in the admin queue with a note. */
    RefundDtos.RefundRequestResponse keepPending(Integer id, String note);

    RefundDtos.RefundRequestResponse reject(Integer id, UUID reviewer, String note);

    List<RefundDtos.RefundRequestResponse> findMine(String email);

    RefundDtos.AdminPage findForAdmin(String status, int page, int size);
}
