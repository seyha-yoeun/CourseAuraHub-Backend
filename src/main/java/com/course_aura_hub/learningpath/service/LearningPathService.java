package com.course_aura_hub.learningpath.service;

import com.course_aura_hub.learningpath.dto.LearningPathEnrollResponse;
import com.course_aura_hub.learningpath.dto.LearningPathProgressResponse;
import com.course_aura_hub.learningpath.dto.LearningPathRequest;
import com.course_aura_hub.learningpath.dto.LearningPathResponse;

import java.util.List;

public interface LearningPathService {

    /** Public: published paths with their published courses. */
    List<LearningPathResponse> findPublished();

    LearningPathResponse findPublished(String slug);

    List<LearningPathResponse> findAllForAdmin();

    LearningPathResponse create(LearningPathRequest request);

    LearningPathResponse update(String slug, LearningPathRequest request);

    /** Certificates already issued for the path are kept. */
    void delete(String slug);

    /** Published paths the learner has started, with progress. Issues any path certificate now earned. */
    List<LearningPathProgressResponse> findMine(String email);

    /** Progress through one published path. Issues the path certificate once every course is complete. */
    LearningPathProgressResponse progress(String email, String slug);

    /** Enrolls in the path's free courses and puts the paid ones in the cart. */
    LearningPathEnrollResponse enroll(String email, String slug);

    /** The learner's own path certificate as a PDF. */
    byte[] certificatePdf(String email, String certificateNumber, String language);
}
