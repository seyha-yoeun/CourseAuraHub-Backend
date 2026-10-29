package com.course_aura_hub.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "CSRF token for cookie-authenticated state-changing requests")
public record CsrfTokenResponse(String headerName, String parameterName, String token) {
}
