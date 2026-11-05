package com.course_aura_hub.review.repository;

import com.course_aura_hub.review.model.ReviewVote;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewVoteRepository extends JpaRepository<ReviewVote, ReviewVote.Id> {

    long countByIdReviewId(Integer reviewId);
}
