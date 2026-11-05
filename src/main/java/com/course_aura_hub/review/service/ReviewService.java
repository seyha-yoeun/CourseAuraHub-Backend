package com.course_aura_hub.review.service;

import com.course_aura_hub.common.security.Actor;
import com.course_aura_hub.review.dto.ReviewPageResponse;
import com.course_aura_hub.review.dto.ReviewRequest;
import com.course_aura_hub.review.dto.ReviewResponse;

import java.util.Optional;

public interface ReviewService {

    ReviewPageResponse findByCourse(String courseSlug, int page, int size, boolean includeUnpublished);

    /** @param sort newest (default) or helpful */
    ReviewPageResponse findByCourse(String courseSlug, int page, int size, boolean includeUnpublished, String sort);

    /** The course staff's public reply to a review; replaces an earlier one. */
    ReviewResponse reply(Actor actor, String courseSlug, Integer reviewId, String body);

    void deleteReply(Actor actor, String courseSlug, Integer reviewId);

    /** Marks a review helpful, or takes that back. Idempotent. Not for your own review. */
    ReviewResponse setHelpful(String email, Integer reviewId, boolean helpful);

    /** Recomputes a course's rating from its visible reviews, for moderation. */
    void refreshRating(Integer courseId);

    Optional<ReviewResponse> findMine(String email, String courseSlug);

    ReviewResponse upsertMine(String email, String courseSlug, ReviewRequest request);

    void deleteMine(String email, String courseSlug);

    void deleteAsAdmin(String courseSlug, Integer reviewId);
}
