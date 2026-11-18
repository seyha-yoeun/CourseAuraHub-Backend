package com.course_aura_hub.subscription.repository;

import com.course_aura_hub.subscription.model.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Integer> {

    List<SubscriptionPlan> findByActiveTrueOrderByPriceAsc();

    List<SubscriptionPlan> findAllByOrderByCreatedAtDesc();

    Optional<SubscriptionPlan> findByCode(String code);

    boolean existsByCode(String code);
}
