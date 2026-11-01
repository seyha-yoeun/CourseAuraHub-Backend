package com.course_aura_hub.membercard.dto;

import java.time.Instant;
import java.time.LocalDate;

public record MemberCardApplicationResponse(
        Integer id,
        Integer studentId,
        String studentEmail,
        String fullName,
        String phone,
        LocalDate dateOfBirth,
        String address,
        String status,
        String reviewNote,
        Instant createdAt,
        Instant reviewedAt
) {
}
