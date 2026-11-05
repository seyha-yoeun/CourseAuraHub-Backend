package com.course_aura_hub.organization.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class OrganizationDtos {

    private OrganizationDtos() {
    }

    public record CreateRequest(
            @NotBlank @Size(max = 150, message = "Name must not exceed 150 characters") String name,
            @NotBlank @Email(message = "Owner email must be a valid email") String ownerEmail
    ) {
    }

    public record SeatRequest(@NotBlank String courseSlug, @NotNull @Min(0) @Max(100000) Integer total) {
    }

    /** @param role member or manager */
    public record InviteRequest(@NotBlank @Email String email, String role) {
    }

    public record AcceptRequest(@NotBlank String token) {
    }

    public record AssignRequest(@NotNull UUID userId) {
    }

    public record Summary(String slug, String name, String role, int members) {
    }

    public record Seat(String courseSlug, String courseTitle, int total, int used) {
    }

    /** Members are listed for managers only; email is shown because managers invite people by it. */
    public record Member(UUID userId, String name, String email, String role, Instant joinedAt) {
    }

    public record Detail(String slug, String name, String role, List<Seat> seats, List<Member> members) {
    }

    /** @param inviteUrl the link to share; it is also emailed to the invitee */
    public record Invite(Integer id, String email, String role, Instant expiresAt, String inviteUrl) {
    }

    public record CourseProgress(String courseSlug, String courseTitle, int completedLessons, int totalLessons,
                                 boolean completed, String certificateNumber) {
    }

    public record MemberProgress(UUID userId, String name, String role, List<CourseProgress> courses) {
    }
}
