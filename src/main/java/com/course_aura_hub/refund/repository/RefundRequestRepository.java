package com.course_aura_hub.refund.repository;

import com.course_aura_hub.refund.model.RefundRequest;
import com.course_aura_hub.refund.model.RefundRequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RefundRequestRepository extends JpaRepository<RefundRequest, Integer> {

    @EntityGraph(attributePaths = {"order", "order.course", "order.student"})
    List<RefundRequest> findByStudentIdOrderByCreatedAtDescIdDesc(Integer studentId);

    @EntityGraph(attributePaths = {"order", "order.course", "order.student"})
    Page<RefundRequest> findByStatusOrderByCreatedAtAscIdAsc(RefundRequestStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"order", "order.course", "order.student"})
    Page<RefundRequest> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    boolean existsByOrderIdAndStatusIn(Integer orderId, List<RefundRequestStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RefundRequest r join fetch r.order o join fetch o.course join fetch o.student where r.id = :id")
    Optional<RefundRequest> findByIdForUpdate(@Param("id") Integer id);
}
