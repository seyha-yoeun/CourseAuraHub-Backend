package com.course_aura_hub.audit.controller;

import com.course_aura_hub.audit.dto.AdminAuditPageResponse;
import com.course_aura_hub.audit.dto.AdminAuditQuery;
import com.course_aura_hub.audit.service.AdminAuditService;
import com.course_aura_hub.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequiredArgsConstructor
@Tag(name = "Audit log", description = "Write actions taken by admins and instructors")
public class AdminAuditController {

    private final AdminAuditService auditService;

    @GetMapping("/api/v1/admin/audit-log")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Audit log",
            description = "Admin-only. Every create, update and delete by an admin or instructor, newest first. "
                    + "Request bodies are never stored."
    )
    public ResponseEntity<ApiResponse<AdminAuditPageResponse>> search(
            @Parameter(description = "Exact email of the person who acted")
            @RequestParam(required = false) String actor,
            @Parameter(description = "Such as courses, coupons, orders, course-reviews or accounts")
            @RequestParam(required = false) String resource,
            @Parameter(description = "Id or slug of the changed resource")
            @RequestParam(required = false) String resourceId,
            @Parameter(description = "Handler name, such as CouponController.update")
            @RequestParam(required = false) String action,
            @Parameter(description = "success or failure")
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        AdminAuditQuery query = new AdminAuditQuery(actor, resource, resourceId, action, outcome, from, to);
        return ResponseEntity.ok(ApiResponse.success(auditService.search(query, page, size)));
    }
}
