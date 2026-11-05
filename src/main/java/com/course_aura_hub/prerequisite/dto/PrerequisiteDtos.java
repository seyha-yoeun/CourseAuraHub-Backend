package com.course_aura_hub.prerequisite.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class PrerequisiteDtos {

    private PrerequisiteDtos() {
    }

    public record Item(@NotBlank String courseSlug, Boolean hard) {
    }

    /** Replaces the course's prerequisites, in this order. */
    public record SetRequest(@NotNull @Size(max = 10, message = "A course has at most 10 prerequisites") @Valid List<Item> prerequisites) {
    }

    /** @param completed whether the viewer finished it; null for visitors */
    public record Prerequisite(String courseSlug, String courseTitle, boolean hard, Boolean completed) {
    }

    /** @param canEnroll false while a hard prerequisite is incomplete; true for visitors, who can't enroll anyway */
    public record Requirements(boolean canEnroll, List<Prerequisite> prerequisites) {
    }
}
