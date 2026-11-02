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

    boolean existsByCardId(Integer cardId);

    @org.springframework.data.jpa.repository.Query("""
            select t.type as type, coalesce(sum(t.amount), 0) as total
            from MemberCardTransaction t
            where t.createdAt >= :since
            group by t.type
            """)
    java.util.List<TypeTotal> sumByTypeSince(@org.springframework.data.repository.query.Param("since") java.time.Instant since);

    interface TypeTotal {
        MemberCardTransactionType getType();

        java.math.BigDecimal getTotal();
    }

    boolean existsByTypeAndReference(MemberCardTransactionType type, String reference);
}
