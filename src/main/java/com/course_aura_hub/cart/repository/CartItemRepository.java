package com.course_aura_hub.cart.repository;

import com.course_aura_hub.cart.model.CartItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Integer> {

    @EntityGraph(attributePaths = "course")
    List<CartItem> findByStudentIdOrderByAddedAtDescIdDesc(Integer studentId);

    boolean existsByStudentIdAndCourseId(Integer studentId, Integer courseId);

    void deleteByStudentIdAndCourseId(Integer studentId, Integer courseId);
}
