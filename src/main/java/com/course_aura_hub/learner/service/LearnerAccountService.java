package com.course_aura_hub.learner.service;

import com.course_aura_hub.student.model.Student;

import java.util.Optional;

/**
 * Resolves the {@link Student} learner record that belongs to an authenticated user.
 */
public interface LearnerAccountService {

    /**
     * Returns the learner record for the user, creating and linking one on first use.
     */
    Student currentStudent(String email);

    /**
     * Returns the learner record for the user without creating one.
     */
    Optional<Student> findCurrentStudent(String email);
}
