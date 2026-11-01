package com.course_aura_hub.membercard.repository;

import com.course_aura_hub.membercard.model.MemberCardTransaction;
import com.course_aura_hub.membercard.model.MemberCardTransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberCardTransactionRepository extends JpaRepository<MemberCardTransaction, Long> {

    Page<MemberCardTransaction> findByCardId(Integer cardId, Pageable pageable);

    Optional<MemberCardTransaction> findByCardIdAndIdempotencyKey(Integer cardId, String idempotencyKey);

    boolean existsByTypeAndReference(MemberCardTransactionType type, String reference);
}
