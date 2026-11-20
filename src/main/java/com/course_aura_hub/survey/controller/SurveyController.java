package com.course_aura_hub.survey.controller;

import com.course_aura_hub.auth.security.AuthUserPrincipal;
import com.course_aura_hub.common.dto.ApiResponse;
import com.course_aura_hub.common.security.Actor;
import com.course_aura_hub.survey.dto.SurveyDtos;
import com.course_aura_hub.survey.service.SurveyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Course surveys", description = "Feedback surveys staff set up and learners answer once")
public class SurveyController {

    private static final String STAFF = "hasAnyRole('ADMIN', 'INSTRUCTOR')";
    private static final String MANAGE = "/api/v1/instructor/courses/{slug}/survey";
    private static final String LEARNER = "/api/v1/me/courses/{slug}/survey";

    private final SurveyService surveyService;

    @GetMapping(MANAGE)
    @PreAuthorize(STAFF)
    @Operation(summary = "A course's survey", description = "Course managers. Null when the course has none.")
    public ResponseEntity<ApiResponse<SurveyDtos.SurveyView>> survey(@PathVariable String slug,
                                                                     @AuthenticationPrincipal AuthUserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.success(surveyService.findForManager(Actor.of(user), slug)));
    }

    @PutMapping(MANAGE)
    @PreAuthorize(STAFF)
    @Operation(summary = "Create or replace a course's survey", description = "Course managers. trigger is percent_50 "
            + "or completion. Up to 10 rating, choice or text questions; a 0-10 recommendation question is always added.")
    public ResponseEntity<ApiResponse<SurveyDtos.SurveyView>> save(@PathVariable String slug,
                                                                   @Valid @RequestBody SurveyDtos.SurveyRequest request,
                                                                   @AuthenticationPrincipal AuthUserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.success("Survey saved", surveyService.save(Actor.of(user), slug, request)));
    }

    @DeleteMapping(MANAGE)
    @PreAuthorize(STAFF)
    @Operation(summary = "Delete a course's survey", description = "Course managers. Its responses are deleted too; "
            + "set active to false to stop asking and keep them.")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String slug,
                                                    @AuthenticationPrincipal AuthUserPrincipal user) {
        surveyService.delete(Actor.of(user), slug);
        return ResponseEntity.ok(ApiResponse.success("Survey deleted", null));
    }

    @GetMapping(MANAGE + "/results")
    @PreAuthorize(STAFF)
    @Operation(summary = "Survey results", description = "Course managers. NPS and per-question aggregates; "
            + "no answer is shown with who gave it.")
    public ResponseEntity<ApiResponse<SurveyDtos.Results>> results(@PathVariable String slug,
                                                                   @AuthenticationPrincipal AuthUserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.success(surveyService.results(Actor.of(user), slug)));
    }

    @GetMapping(LEARNER)
    @Operation(summary = "The survey for a course I'm taking", description = "Enrolled learners. eligible says whether "
            + "I've reached the point where I'm asked.")
    public ResponseEntity<ApiResponse<SurveyDtos.LearnerSurvey>> mine(@PathVariable String slug, Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(surveyService.findForLearner(authentication.getName(), slug)));
    }

    @PostMapping(LEARNER)
    @Operation(summary = "Answer a course's survey", description = "Once, after reaching the survey's point in the course.")
    public ResponseEntity<ApiResponse<Void>> answer(@PathVariable String slug,
                                                    @Valid @RequestBody SurveyDtos.AnswerRequest request,
                                                    Authentication authentication) {
        surveyService.answer(authentication.getName(), slug, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Thanks for your feedback", null));
    }
}
