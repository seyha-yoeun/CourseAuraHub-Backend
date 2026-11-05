package com.course_aura_hub.gift.repository;

import com.course_aura_hub.gift.model.AccessCodeRedemption;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccessCodeRedemptionRepository extends JpaRepository<AccessCodeRedemption, AccessCodeRedemption.Id> {
}
