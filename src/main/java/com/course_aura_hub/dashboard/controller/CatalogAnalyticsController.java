package com.course_aura_hub.dashboard.controller;

import com.course_aura_hub.auth.security.AuthUserPrincipal;
import com.course_aura_hub.auth.security.ClientInfoResolver;
import com.course_aura_hub.common.dto.ApiResponse;
import com.course_aura_hub.dashboard.dto.FunnelDtos;
import com.course_aura_hub.dashboard.service.CatalogAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@Tag(name = "Funnel analytics", description = "Anonymous catalog events, the enrollment funnel and retention")
public class CatalogAnalyticsController {

    private final CatalogAnalyticsService analyticsService;
    private final ClientInfoResolver clientInfoResolver;

    @PostMapping("/api/v1/events")
    @Operation(summary = "Record a catalog event", description = "Public and rate-limited. event is view, add_to_cart "
            + "or checkout_start. Visitors are stored only as a salted hash; repeats within 30 minutes count once.")
    public ResponseEntity<Void> record(@Valid @RequestBody FunnelDtos.EventRequest request,
                                       @AuthenticationPrincipal AuthUserPrincipal user,
                                       HttpServletRequest httpRequest) {
        boolean accepted = analyticsService.record(request, user == null ? null : user.getUserId(),
                clientInfoResolver.resolveClientIp(httpRequest), httpRequest.getHeader(HttpHeaders.USER_AGENT));
        return ResponseEntity.status(accepted ? HttpStatus.NO_CONTENT : HttpStatus.TOO_MANY_REQUESTS).build();
    }

    @GetMapping("/api/v1/stats/funnel")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Enrollment funnel", description = "Admin-only. Visitors who viewed, added to cart and started "
            + "checkout, then learners who enrolled in the range and how far they got. Last 30 days by default.")
    public ResponseEntity<ApiResponse<FunnelDtos.Funnel>> funnel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String course
    ) {
        return ResponseEntity.ok(ApiResponse.success(analyticsService.funnel(from, to, course)));
    }

    @GetMapping("/api/v1/stats/retention")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Learner retention", description = "Admin-only. Learners by joining month and the share who "
            + "completed a lesson in each month since. Up to 24 months.")
    public ResponseEntity<ApiResponse<FunnelDtos.Retention>> retention(@RequestParam(defaultValue = "6") int months) {
        return ResponseEntity.ok(ApiResponse.success(analyticsService.retention(months)));
    }
}
