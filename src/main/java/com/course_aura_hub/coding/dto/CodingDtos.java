package com.course_aura_hub.coding.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Coding exercise shapes. Learners see examples in full and hidden tests only as a count. */
public final class CodingDtos {

    private CodingDtos() {
    }

    public record Case(
            @Size(max = 10_000, message = "Input must not exceed 10000 characters") String input,
            @NotNull(message = "Expected output is required")
            @Size(max = 10_000, message = "Expected output must not exceed 10000 characters") String expectedOutput
    ) {
    }

    public record ExerciseRequest(
            @NotBlank(message = "Language is required") String language,
            @Size(max = 10_000) String instructions,
            @Size(max = 20_000) String starterCode,
            @NotNull @Size(max = 10) List<@Valid Case> examples,
            @NotNull @Size(min = 1, max = 20, message = "Add 1 to 20 hidden tests") List<@Valid Case> tests,
            @DecimalMin(value = "0.5", message = "At least half a second") @DecimalMax(value = "15", message = "At most 15 seconds")
            BigDecimal timeLimitSeconds,
            @Min(value = 16, message = "At least 16 MB") @Max(value = 512, message = "At most 512 MB") Integer memoryLimitMb
    ) {
    }

    /** Staff view, with hidden tests. */
    public record Exercise(Integer lessonId, String language, String instructions, String starterCode, List<Case> examples,
                           List<Case> tests, BigDecimal timeLimitSeconds, int memoryLimitMb, List<String> languages) {
    }

    public record LastSubmission(String code, boolean passed, int passedTests, int totalTests, String verdict,
                                 Instant createdAt) {
    }

    /** @param runsEnabled whether the server can run code right now */
    public record LearnerExercise(Integer lessonId, String language, String instructions, String starterCode,
                                  List<Case> examples, int hiddenTests, BigDecimal timeLimitSeconds, int memoryLimitMb,
                                  boolean passed, LastSubmission lastSubmission, boolean runsEnabled) {
    }

    public record RunRequest(
            @NotNull(message = "Write some code first") @Size(max = 65_536, message = "Code is too long") String code
    ) {
    }

    /** One visible example's result, with the program's own output. */
    public record ExampleResult(String input, String expectedOutput, String actualOutput, boolean passed) {
    }

    /**
     * @param verdict  accepted, wrong_answer, compile_error, runtime_error, time_limit or error
     * @param message  compiler or runtime output for examples; for hidden tests only which one failed
     */
    public record RunResult(boolean passed, int passedTests, int totalTests, String verdict, String message,
                            List<ExampleResult> examples, boolean lessonCompleted) {
    }
}
