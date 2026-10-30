package com.course_aura_hub.learner.service;

import com.course_aura_hub.learner.dto.WishlistItemResponse;

import java.util.List;

public interface WishlistService {

    List<WishlistItemResponse> findMine(String email);

    WishlistItemResponse add(String email, String courseSlug);

    void remove(String email, String courseSlug);

    void removeIfPresent(Integer studentId, Integer courseId);
}
