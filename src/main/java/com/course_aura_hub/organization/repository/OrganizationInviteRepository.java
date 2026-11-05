package com.course_aura_hub.organization.repository;

import com.course_aura_hub.organization.model.OrganizationInvite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrganizationInviteRepository extends JpaRepository<OrganizationInvite, Integer> {

    Optional<OrganizationInvite> findByTokenHash(String tokenHash);
}
