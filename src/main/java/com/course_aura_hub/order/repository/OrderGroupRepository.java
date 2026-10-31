package com.course_aura_hub.order.repository;

import com.course_aura_hub.order.model.OrderGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderGroupRepository extends JpaRepository<OrderGroup, Integer> {

    Optional<OrderGroup> findByGroupNumber(String groupNumber);
}
