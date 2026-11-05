package com.course_aura_hub.revision.service;

import com.course_aura_hub.common.security.Actor;
import com.course_aura_hub.course.dto.CourseUpdateRequest;
import com.course_aura_hub.course.model.Course;
import com.course_aura_hub.revision.dto.RevisionDtos;

import java.util.List;
import java.util.UUID;

public interface CourseRevisionService {

    /**
     * Handles an instructor's edit to a live course: fields allowed to change at once are applied, the rest are folded
     * into the course's pending revision.
     */
    void propose(Actor actor, Course course, CourseUpdateRequest request);

    /** The course's revision waiting for review, or null. */
    RevisionDtos.RevisionResponse findPending(Integer courseId);

    List<RevisionDtos.RevisionResponse> history(Actor actor, String slug);

    RevisionDtos.RevisionResponse updateSummary(Actor actor, String slug, String summary);

    void withdraw(Actor actor, String slug);

    List<RevisionDtos.RevisionResponse> findAwaitingReview();

    RevisionDtos.RevisionResponse find(Integer id);

    RevisionDtos.RevisionResponse approve(Integer id, UUID adminId, String note);

    RevisionDtos.RevisionResponse reject(Integer id, UUID adminId, String note);

    /** Approved changes to a published course, newest first. Public. */
    List<RevisionDtos.ChangelogEntry> changelog(String slug);
}
