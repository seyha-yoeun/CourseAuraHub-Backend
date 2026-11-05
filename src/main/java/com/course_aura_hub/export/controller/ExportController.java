package com.course_aura_hub.export.controller;

import com.course_aura_hub.auth.security.AuthUserPrincipal;
import com.course_aura_hub.common.exception.BusinessRuleException;
import com.course_aura_hub.common.security.Actor;
import com.course_aura_hub.export.service.ExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.function.Consumer;

/** CSV downloads. Every export is recorded in the audit log, because they contain personal data. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Exports", description = "CSV downloads for admins and course staff")
public class ExportController {

    private static final String ADMIN = "/api/v1/admin/exports";

    private final ExportService exportService;

    @GetMapping(ADMIN + "/orders.csv")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Orders CSV", description = "Admin-only. from and to are inclusive UTC dates (yyyy-mm-dd); status filters.")
    public void orders(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       @RequestParam(required = false) String status,
                       HttpServletResponse response) throws IOException {
        requireRange(from, to);
        exportService.validateOrderStatus(status);
        write(response, "orders", out -> exportService.orders(out, from, to, status));
    }

    @GetMapping(ADMIN + "/enrollments.csv")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Enrollments CSV", description = "Admin-only.")
    public void enrollments(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                            HttpServletResponse response) throws IOException {
        requireRange(from, to);
        write(response, "enrollments", out -> exportService.enrollments(out, from, to));
    }

    @GetMapping(ADMIN + "/learners.csv")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Learners CSV", description = "Admin-only. Includes emails and spending.")
    public void learners(HttpServletResponse response) throws IOException {
        write(response, "learners", exportService::learners);
    }

    @GetMapping(ADMIN + "/member-card-ledger.csv")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Member card ledger CSV", description = "Admin-only. Every balance change.")
    public void memberCardLedger(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                 HttpServletResponse response) throws IOException {
        requireRange(from, to);
        write(response, "member-card-ledger", out -> exportService.memberCardLedger(out, from, to));
    }

    @GetMapping(ADMIN + "/audit-log.csv")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Audit log CSV", description = "Admin-only.")
    public void auditLog(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                         HttpServletResponse response) throws IOException {
        requireRange(from, to);
        write(response, "audit-log", out -> exportService.auditLog(out, from, to));
    }

    @GetMapping("/api/v1/instructor/courses/{slug}/exports/learners.csv")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    @Operation(summary = "Course learners CSV", description = "Admins and the course's instructor. Names and progress, never emails.")
    public void courseLearners(@PathVariable String slug, @AuthenticationPrincipal AuthUserPrincipal user,
                               HttpServletResponse response) throws IOException {
        Actor actor = Actor.of(user);
        exportService.requireCourseManager(actor, slug);
        write(response, slug + "-learners", out -> exportService.courseLearners(out, actor, slug));
    }

    @GetMapping("/api/v1/instructor/courses/{slug}/exports/grades.csv")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    @Operation(summary = "Course grades CSV", description = "Admins and the course's instructor. Assignment submissions and scores.")
    public void courseGrades(@PathVariable String slug, @AuthenticationPrincipal AuthUserPrincipal user,
                             HttpServletResponse response) throws IOException {
        Actor actor = Actor.of(user);
        exportService.requireCourseManager(actor, slug);
        write(response, slug + "-grades", out -> exportService.courseGrades(out, actor, slug));
    }

    private static void requireRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessRuleException("from must not be after to");
        }
    }

    /** Callers check access and filters first: once the CSV starts, errors can't become a JSON response. */
    private static void write(HttpServletResponse response, String name, Consumer<Writer> body) throws IOException {
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(name + "-" + LocalDate.now() + ".csv").build().toString());
        Writer out = new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8);
        // A byte-order mark makes Excel read the file as UTF-8, so Khmer names survive
        out.write('﻿');
        body.accept(out);
        out.flush();
    }
}
