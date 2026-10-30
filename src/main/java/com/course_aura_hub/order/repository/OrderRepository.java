package com.course_aura_hub.order.repository;

import com.course_aura_hub.order.model.Order;
import com.course_aura_hub.order.model.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Integer> {

    @EntityGraph(attributePaths = {"student", "course"})
    Optional<Order> findByOrderNumber(String orderNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.orderNumber = :orderNumber")
    Optional<Order> findByOrderNumberForUpdate(@Param("orderNumber") String orderNumber);

    Optional<Order> findByStudentIdAndCourseIdAndStatus(Integer studentId, Integer courseId, OrderStatus status);

    @EntityGraph(attributePaths = "course")
    List<Order> findByStudentIdOrderByCreatedAtDescIdDesc(Integer studentId);

    @EntityGraph(attributePaths = {"student", "course"})
    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    long countByCouponIdAndStatus(Integer couponId, OrderStatus status);

    /** Paid orders plus checkouts still inside their reservation window. */
    @Query("""
            select count(o) from Order o
            where o.coupon.id = :couponId and o.id <> :excludeOrderId
              and (o.status = com.course_aura_hub.order.model.OrderStatus.PAID
                   or (o.status = com.course_aura_hub.order.model.OrderStatus.PENDING and o.createdAt > :reservedSince))
            """)
    long countActiveUses(
            @Param("couponId") Integer couponId,
            @Param("excludeOrderId") Integer excludeOrderId,
            @Param("reservedSince") Instant reservedSince
    );

    @Query("""
            select count(o) from Order o
            where o.coupon.id = :couponId and o.student.id = :studentId and o.id <> :excludeOrderId
              and o.status in (com.course_aura_hub.order.model.OrderStatus.PAID,
                               com.course_aura_hub.order.model.OrderStatus.PENDING)
            """)
    long countLearnerUses(
            @Param("couponId") Integer couponId,
            @Param("studentId") Integer studentId,
            @Param("excludeOrderId") Integer excludeOrderId
    );

    @Override
    @EntityGraph(attributePaths = {"student", "course"})
    Page<Order> findAll(Pageable pageable);
}
