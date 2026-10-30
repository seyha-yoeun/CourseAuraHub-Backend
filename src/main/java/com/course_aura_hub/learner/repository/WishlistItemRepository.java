package com.course_aura_hub.learner.repository;

import com.course_aura_hub.learner.model.WishlistItem;
import com.course_aura_hub.learner.model.WishlistItemId;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WishlistItemRepository extends JpaRepository<WishlistItem, WishlistItemId> {

    @EntityGraph(attributePaths = {"course"})
    List<WishlistItem> findByStudentIdOrderByCreatedAtDesc(Integer studentId);

    Optional<WishlistItem> findByStudentIdAndCourseId(Integer studentId, Integer courseId);
}
