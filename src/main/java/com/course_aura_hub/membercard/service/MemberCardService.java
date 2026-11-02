package com.course_aura_hub.membercard.service;

import com.course_aura_hub.membercard.dto.MemberCardAdjustmentRequest;
import com.course_aura_hub.membercard.dto.MemberCardApplicationPageResponse;
import com.course_aura_hub.membercard.dto.MemberCardApplicationRequest;
import com.course_aura_hub.membercard.dto.MemberCardApplicationResponse;
import com.course_aura_hub.membercard.dto.MemberCardCreateRequest;
import com.course_aura_hub.membercard.dto.MemberCardOverviewResponse;
import com.course_aura_hub.membercard.dto.MemberCardPageResponse;
import com.course_aura_hub.membercard.dto.MemberCardResponse;
import com.course_aura_hub.membercard.dto.MemberCardTransactionPageResponse;
import com.course_aura_hub.membercard.dto.MemberCardTransactionResponse;
import com.course_aura_hub.membercard.dto.MemberCardUpdateRequest;

import java.math.BigDecimal;
import java.util.UUID;

public interface MemberCardService {

    MemberCardOverviewResponse findMine(String email);

    MemberCardApplicationResponse apply(String email, MemberCardApplicationRequest request);

    MemberCardTransactionPageResponse findMyTransactions(String email, int page, int size);

    MemberCardApplicationPageResponse findApplications(String status, int page, int size);

    MemberCardApplicationResponse approve(Integer applicationId, UUID adminId);

    MemberCardApplicationResponse reject(Integer applicationId, UUID adminId, String note);

    MemberCardPageResponse findCards(String search, int page, int size);

    MemberCardResponse findCard(Integer cardId);

    /** Issues a card without an application. A pending application from this learner is approved with it. */
    MemberCardResponse create(UUID adminId, MemberCardCreateRequest request);

    MemberCardResponse update(Integer cardId, UUID adminId, MemberCardUpdateRequest request);

    /** Only for a card with no transactions: the ledger is a money record, so a used card is suspended instead. */
    void delete(Integer cardId, UUID adminId);

    MemberCardTransactionPageResponse findTransactions(Integer cardId, int page, int size);

    MemberCardResponse setStatus(Integer cardId, UUID adminId, String status);

    /** Safe to repeat: a key already used on this card returns the original result without moving money. */
    MemberCardTransactionResponse adjust(Integer cardId, UUID adminId, MemberCardAdjustmentRequest request);

    /** Adds an approved top-up. The reference makes a repeated approval impossible. */
    void creditTopUp(Integer cardId, UUID adminId, BigDecimal amount, String reference, String note);

    /** Fails early, before an order is opened, when this learner can't pay by card. */
    void requireUsableCard(String email);

    /** Pays for an order or order group. Runs in the caller's transaction, so it commits with the enrollment. */
    void charge(Integer studentId, BigDecimal amount, String reference, String note);

    /** Returns a card payment. Works on a suspended card, because the money belongs to the learner. */
    void refund(Integer studentId, BigDecimal amount, String reference, String note);
}
