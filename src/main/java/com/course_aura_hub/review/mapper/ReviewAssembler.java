package com.course_aura_hub.review.mapper;

import com.course_aura_hub.review.dto.ReviewResponse;
import com.course_aura_hub.review.model.CourseReview;
import org.springframework.stereotype.Component;

@Component
public class ReviewAssembler {

    /**
     * Exposes only public reviewer details; never the learner's email or spending.
     */
    public ReviewResponse toResponse(CourseReview review) {
        return new ReviewResponse(
                review.getId(),
                review.getRating().intValue(),
                review.getComment(),
                review.getStudent().getName(),
                review.getStudent().getAvatar(),
                review.getCreatedAt(),
                review.getUpdatedAt()
        );
    }
}
