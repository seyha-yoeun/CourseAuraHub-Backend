package com.course_aura_hub.subscription.repository;

import com.course_aura_hub.subscription.model.SubscriptionPayment;
import com.course_aura_hub.subscription.model.SubscriptionPaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SubscriptionPaymentRepository extends JpaRepository<SubscriptionPayment, Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from SubscriptionPayment p where p.paymentNumber = :number")
    Optional<SubscriptionPayment> findByNumberForUpdate(@Param("number") String number);

    List<SubscriptionPayment> findTop12BySubscriptionIdOrderByCreatedAtDescIdDesc(Integer subscriptionId);

    List<SubscriptionPayment> findBySubscriptionIdAndStatus(Integer subscriptionId, SubscriptionPaymentStatus status);
}
