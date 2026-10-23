package com.course_aura_hub.auth.dto;

public record CsrfTokenResponse(
        String headerName,
        String parameterName,
        String token
) {
}
