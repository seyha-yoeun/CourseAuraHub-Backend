package com.course_aura_hub.membercard.repository;

import com.course_aura_hub.membercard.model.MemberCard;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MemberCardRepository extends JpaRepository<MemberCard, Integer> {

    Optional<MemberCard> findByStudentId(Integer studentId);

    boolean existsByStudentId(Integer studentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from MemberCard c where c.id = :id")
    Optional<MemberCard> findByIdForUpdate(@Param("id") Integer id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from MemberCard c where c.student.id = :studentId")
    Optional<MemberCard> findByStudentIdForUpdate(@Param("studentId") Integer studentId);

    /** @param pattern a lowercase LIKE pattern; "%" matches every card */
    @EntityGraph(attributePaths = "student")
    @Query("""
            select c from MemberCard c
            where lower(c.holderName) like :pattern
               or lower(c.cardNumber) like :pattern
               or lower(c.student.email) like :pattern
            """)
    Page<MemberCard> search(@Param("pattern") String pattern, Pageable pageable);
}
