package com.course_aura_hub.learningpath.dto;

import java.util.List;

/**
 * What enrolling in a path did, by course slug. Free courses are enrolled; paid ones go to the cart for checkout.
 */
public record LearningPathEnrollResponse(
        List<String> enrolled,
        List<String> addedToCart,
        List<String> alreadyEnrolled
) {
}
