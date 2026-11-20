package com.course_aura_hub.survey.repository;

import com.course_aura_hub.survey.model.CourseSurvey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CourseSurveyRepository extends JpaRepository<CourseSurvey, Integer> {

    Optional<CourseSurvey> findByCourseId(Integer courseId);
}
