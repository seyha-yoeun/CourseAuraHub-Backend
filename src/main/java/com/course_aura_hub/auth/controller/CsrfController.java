package com.course_aura_hub.auth.controller;

import com.course_aura_hub.auth.dto.CsrfTokenResponse;
import com.course_aura_hub.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "CSRF bootstrap for cookie-authenticated browser writes")
public class CsrfController {

    @GetMapping("/csrf")
    @Operation(
            summary = "Get CSRF token",
            description = "Returns the expected header, parameter, and token values. Also sets the XSRF-TOKEN cookie."
    )
    public ResponseEntity<ApiResponse<CsrfTokenResponse>> csrf(CsrfToken csrfToken) {
        CsrfTokenResponse response = new CsrfTokenResponse(
                csrfToken.getHeaderName(),
                csrfToken.getParameterName(),
                csrfToken.getToken()
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
