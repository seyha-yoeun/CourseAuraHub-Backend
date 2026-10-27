package com.course_aura_hub.review.service;

import com.course_aura_hub.review.dto.ReviewPageResponse;
import com.course_aura_hub.review.dto.ReviewRequest;
import com.course_aura_hub.review.dto.ReviewResponse;

import java.util.Optional;

public interface ReviewService {

    ReviewPageResponse findByCourse(String courseSlug, int page, int size, boolean includeUnpublished);

    Optional<ReviewResponse> findMine(String email, String courseSlug);

    ReviewResponse upsertMine(String email, String courseSlug, ReviewRequest request);

    void deleteMine(String email, String courseSlug);

    void deleteAsAdmin(String courseSlug, Integer reviewId);
}
