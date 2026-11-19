package com.course_aura_hub.coding.service;

import com.course_aura_hub.coding.dto.CodingDtos;
import com.course_aura_hub.common.security.Actor;

/** Coding exercises for CODE lessons: authoring by staff, sandboxed runs and grading for learners. */
public interface CodingService {

    /** The exercise with hidden tests, or null when the lesson has none yet. Course managers only. */
    CodingDtos.Exercise findForStaff(Actor actor, Integer lessonId);

    CodingDtos.Exercise save(Actor actor, Integer lessonId, CodingDtos.ExerciseRequest request);

    /** Examples and starter code, never hidden tests. */
    CodingDtos.LearnerExercise findForLearner(Actor actor, Integer lessonId);

    /**
     * Runs the code against the examples and hidden tests in the sandbox. A learner's run is saved, and passing
     * every test completes the lesson. Course staff may run code to check an exercise; their runs aren't saved.
     */
    CodingDtos.RunResult run(Actor actor, Integer lessonId, String code);
}
