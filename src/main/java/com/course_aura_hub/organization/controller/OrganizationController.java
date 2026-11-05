package com.course_aura_hub.organization.controller;

import com.course_aura_hub.auth.security.AuthUserPrincipal;
import com.course_aura_hub.common.dto.ApiResponse;
import com.course_aura_hub.common.security.Actor;
import com.course_aura_hub.organization.dto.OrganizationDtos;
import com.course_aura_hub.organization.service.OrganizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Organizations", description = "Team accounts: seats per course, invites and member progress")
public class OrganizationController {

    private static final String ADMIN = "/api/v1/admin/organizations";
    private static final String MINE = "/api/v1/me/organizations";

    private final OrganizationService organizationService;

    @PostMapping(ADMIN)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create an organization", description = "Admin-only. The owner must already have an account.")
    public ResponseEntity<ApiResponse<OrganizationDtos.Detail>> create(
            @Valid @RequestBody OrganizationDtos.CreateRequest request, @AuthenticationPrincipal AuthUserPrincipal admin) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Organization created", organizationService.create(request, admin.getUserId())));
    }

    @GetMapping(ADMIN)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "All organizations", description = "Admin-only.")
    public ResponseEntity<ApiResponse<List<OrganizationDtos.Summary>>> all() {
        return ResponseEntity.ok(ApiResponse.success(organizationService.findAll()));
    }

    @PutMapping(ADMIN + "/{slug}/seats")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Set seats for a course", description = "Admin-only. Grants or changes the seat count; never below the seats in use.")
    public ResponseEntity<ApiResponse<OrganizationDtos.Seat>> seats(
            @PathVariable String slug, @Valid @RequestBody OrganizationDtos.SeatRequest request) {
        return ResponseEntity.ok(ApiResponse.success(organizationService.setSeats(slug, request)));
    }

    @GetMapping(MINE)
    @Operation(summary = "My organizations", description = "Organizations I belong to, with my role.")
    public ResponseEntity<ApiResponse<List<OrganizationDtos.Summary>>> mine(@AuthenticationPrincipal AuthUserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.success(organizationService.findMine(user.getUserId())));
    }

    @GetMapping(MINE + "/{slug}")
    @Operation(summary = "An organization", description = "Seats for every member; the member list for managers.")
    public ResponseEntity<ApiResponse<OrganizationDtos.Detail>> find(@PathVariable String slug, @AuthenticationPrincipal AuthUserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.success(organizationService.find(Actor.of(user), slug)));
    }

    @PostMapping(MINE + "/{slug}/invites")
    @Operation(summary = "Invite someone", description = "Managers. Emails a link that expires in 14 days; only the owner invites managers.")
    public ResponseEntity<ApiResponse<OrganizationDtos.Invite>> invite(
            @PathVariable String slug, @Valid @RequestBody OrganizationDtos.InviteRequest request,
            @AuthenticationPrincipal AuthUserPrincipal user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Invitation sent", organizationService.invite(Actor.of(user), slug, request)));
    }

    @PostMapping("/api/v1/me/organization-invites/accept")
    @Operation(summary = "Join with an invitation", description = "The invitation must have been sent to my email address.")
    public ResponseEntity<ApiResponse<OrganizationDtos.Summary>> accept(
            @Valid @RequestBody OrganizationDtos.AcceptRequest request, @AuthenticationPrincipal AuthUserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.success("Welcome aboard", organizationService.accept(Actor.of(user), request.token())));
    }

    @PostMapping(MINE + "/{slug}/seats/{courseSlug}/assignments")
    @Operation(summary = "Give a member a seat", description = "Managers. Enrolls the member in the course for free.")
    public ResponseEntity<ApiResponse<OrganizationDtos.Seat>> assign(
            @PathVariable String slug, @PathVariable String courseSlug,
            @Valid @RequestBody OrganizationDtos.AssignRequest request, @AuthenticationPrincipal AuthUserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.success("Seat assigned",
                organizationService.assignSeat(Actor.of(user), slug, courseSlug, request.userId())));
    }

    @DeleteMapping(MINE + "/{slug}/members/{userId}")
    @Operation(summary = "Remove a member", description = "Managers remove members; only the owner removes managers. Course access is kept.")
    public ResponseEntity<ApiResponse<Void>> remove(
            @PathVariable String slug, @PathVariable UUID userId, @AuthenticationPrincipal AuthUserPrincipal user) {
        organizationService.removeMember(Actor.of(user), slug, userId);
        return ResponseEntity.ok(ApiResponse.success("Member removed", null));
    }

    @GetMapping(MINE + "/{slug}/progress")
    @Operation(summary = "Members' progress", description = "Managers. Lessons completed, completion and certificates for each assigned course.")
    public ResponseEntity<ApiResponse<List<OrganizationDtos.MemberProgress>>> progress(
            @PathVariable String slug, @AuthenticationPrincipal AuthUserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.success(organizationService.progress(Actor.of(user), slug)));
    }
}
