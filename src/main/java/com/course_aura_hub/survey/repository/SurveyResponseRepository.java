package com.course_aura_hub.survey.repository;

import com.course_aura_hub.survey.model.SurveyResponse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SurveyResponseRepository extends JpaRepository<SurveyResponse, Integer> {

    boolean existsBySurveyIdAndStudentId(Integer surveyId, Integer studentId);

    List<SurveyResponse> findBySurveyIdOrderByCreatedAtDescIdDesc(Integer surveyId);

    long countBySurveyId(Integer surveyId);
}
