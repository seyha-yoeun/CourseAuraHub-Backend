package com.course_aura_hub.organization.repository;

import com.course_aura_hub.organization.model.OrganizationMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrganizationMemberRepository extends JpaRepository<OrganizationMember, OrganizationMember.Id> {

    List<OrganizationMember> findByIdOrganizationIdOrderByJoinedAtAsc(Integer organizationId);

    List<OrganizationMember> findByIdUserId(UUID userId);

    long countByIdOrganizationId(Integer organizationId);
}
