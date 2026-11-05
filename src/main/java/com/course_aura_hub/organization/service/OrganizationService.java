package com.course_aura_hub.organization.service;

import com.course_aura_hub.common.security.Actor;
import com.course_aura_hub.organization.dto.OrganizationDtos;

import java.util.List;
import java.util.UUID;

public interface OrganizationService {

    OrganizationDtos.Detail create(OrganizationDtos.CreateRequest request, UUID adminId);

    List<OrganizationDtos.Summary> findAll();

    /** Admin: sets how many seats an organization has for a course; never fewer than are in use. */
    OrganizationDtos.Seat setSeats(String slug, OrganizationDtos.SeatRequest request);

    List<OrganizationDtos.Summary> findMine(UUID userId);

    OrganizationDtos.Detail find(Actor actor, String slug);

    OrganizationDtos.Invite invite(Actor actor, String slug, OrganizationDtos.InviteRequest request);

    OrganizationDtos.Summary accept(Actor actor, String token);

    OrganizationDtos.Seat assignSeat(Actor actor, String slug, String courseSlug, UUID memberId);

    void removeMember(Actor actor, String slug, UUID memberId);

    List<OrganizationDtos.MemberProgress> progress(Actor actor, String slug);
}
