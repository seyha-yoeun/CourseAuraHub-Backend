package com.course_aura_hub.learner.controller;

import com.course_aura_hub.common.dto.ApiResponse;
import com.course_aura_hub.learner.dto.LeaderboardDtos;
import com.course_aura_hub.learner.service.LeaderboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Leaderboards", description = "XP rankings and weekly learning goals")
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    @GetMapping("/api/v1/leaderboard")
    @Operation(summary = "Leaderboard", description = "Signed-in users. XP this week or month (UTC): 10 per lesson, "
            + "20 per quiz passed, 100 per certificate. course limits it to one published course. Names are shortened "
            + "and learners can hide themselves.")
    public ResponseEntity<ApiResponse<LeaderboardDtos.Leaderboard>> leaderboard(
            @RequestParam(defaultValue = "week") String scope,
            @RequestParam(required = false) String course,
            @RequestParam(defaultValue = "20") int limit,
            Authentication authentication
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                leaderboardService.leaderboard(authentication.getName(), scope, course, limit)));
    }

    @GetMapping("/api/v1/me/goals")
    @Operation(summary = "My weekly goal", description = "Lessons completed since Monday in timeZone (UTC by default) "
            + "against my target, and whether I appear on leaderboards.")
    public ResponseEntity<ApiResponse<LeaderboardDtos.GoalResponse>> goal(
            @RequestParam(required = false) String timeZone,
            Authentication authentication
    ) {
        return ResponseEntity.ok(ApiResponse.success(leaderboardService.goal(authentication.getName(), timeZone)));
    }

    @PutMapping("/api/v1/me/goals")
    @Operation(summary = "Set my weekly goal", description = "A null weeklyLessonsTarget clears the goal; "
            + "leaderboardVisible false hides me from every leaderboard.")
    public ResponseEntity<ApiResponse<LeaderboardDtos.GoalResponse>> updateGoal(
            @Valid @RequestBody LeaderboardDtos.GoalRequest request,
            @RequestParam(required = false) String timeZone,
            Authentication authentication
    ) {
        return ResponseEntity.ok(ApiResponse.success("Goal saved",
                leaderboardService.updateGoal(authentication.getName(), request, timeZone)));
    }
}
