package com.course_aura_hub.assignment.service;

import com.course_aura_hub.assignment.dto.AssignmentGradeRequest;
import com.course_aura_hub.assignment.dto.AssignmentRequest;
import com.course_aura_hub.assignment.dto.AssignmentResponse;
import com.course_aura_hub.assignment.dto.AssignmentSubmitRequest;
import com.course_aura_hub.assignment.dto.SubmissionPageResponse;
import com.course_aura_hub.assignment.dto.SubmissionReviewResponse;
import com.course_aura_hub.common.security.Actor;

public interface AssignmentService {

    /** @return the lesson's assignment with the caller's submission, or null when it has none */
    AssignmentResponse findForLearner(Actor actor, Integer lessonId);

    /** Replaces any earlier submission, until one has passed. */
    AssignmentResponse submit(Actor actor, Integer lessonId, AssignmentSubmitRequest request);

    AssignmentResponse findForStaff(Actor actor, Integer lessonId);

    AssignmentResponse save(Actor actor, Integer lessonId, AssignmentRequest request);

    void delete(Actor actor, Integer lessonId);

    /** Work waiting to be graded: every course for admins, their own for instructors. Oldest first. */
    SubmissionPageResponse findUngraded(Actor actor, int page, int size);

    /** A passing grade completes the lesson. */
    SubmissionReviewResponse grade(Actor actor, Integer submissionId, AssignmentGradeRequest request);
}
