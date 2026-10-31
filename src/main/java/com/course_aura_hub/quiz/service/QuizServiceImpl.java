package com.course_aura_hub.quiz.service;

import com.course_aura_hub.common.exception.BusinessRuleException;
import com.course_aura_hub.common.exception.ForbiddenActionException;
import com.course_aura_hub.common.exception.ResourceNotFoundException;
import com.course_aura_hub.curriculum.dto.LessonProgressResponse;
import com.course_aura_hub.curriculum.model.Lesson;
import com.course_aura_hub.curriculum.model.LessonType;
import com.course_aura_hub.curriculum.repository.LessonRepository;
import com.course_aura_hub.curriculum.service.LessonProgressService;
import com.course_aura_hub.learner.service.LearnerAccountService;
import com.course_aura_hub.quiz.dto.QuizAdminRequest;
import com.course_aura_hub.quiz.dto.QuizAdminResponse;
import com.course_aura_hub.quiz.dto.QuizResponse;
import com.course_aura_hub.quiz.dto.QuizResultResponse;
import com.course_aura_hub.quiz.model.QuizAttempt;
import com.course_aura_hub.quiz.model.QuizOption;
import com.course_aura_hub.quiz.model.QuizQuestion;
import com.course_aura_hub.quiz.repository.QuizAttemptRepository;
import com.course_aura_hub.quiz.repository.QuizQuestionRepository;
import com.course_aura_hub.student.model.Student;
import com.course_aura_hub.student.repository.StudentCourseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuizServiceImpl implements QuizService {

    private final QuizQuestionRepository questionRepository;
    private final QuizAttemptRepository attemptRepository;
    private final LessonRepository lessonRepository;
    private final LessonProgressService lessonProgressService;
    private final StudentCourseRepository studentCourseRepository;
    private final LearnerAccountService learnerAccountService;

    @Override
    @Transactional(readOnly = true)
    public QuizResponse findForLearner(String email, Integer lessonId) {
        Lesson lesson = findQuizLesson(lessonId);
        Student student = requireEnrollment(email, lesson);

        List<QuizResponse.Question> questions = questionRepository.findByLessonIdOrderByPositionAsc(lessonId).stream()
                .map(question -> new QuizResponse.Question(
                        question.getId(),
                        question.getPrompt(),
                        question.getOptions().stream()
                                .map(option -> new QuizResponse.Option(option.getId(), option.getText()))
                                .toList()
                ))
                .toList();

        return new QuizResponse(
                lessonId,
                lesson.getQuizPassPercent(),
                questions,
                attemptRepository.countByStudentIdAndLessonId(student.getId(), lessonId),
                attemptRepository.findBestScore(student.getId(), lessonId),
                attemptRepository.existsByStudentIdAndLessonIdAndPassedTrue(student.getId(), lessonId)
        );
    }

    @Override
    @Transactional
    public QuizResultResponse submit(String email, Integer lessonId, Map<Integer, Integer> answers) {
        Lesson lesson = findQuizLesson(lessonId);
        Student student = requireEnrollment(email, lesson);

        List<QuizQuestion> questions = questionRepository.findByLessonIdOrderByPositionAsc(lessonId);
        if (questions.isEmpty()) {
            throw new BusinessRuleException("This quiz has no questions yet");
        }
        if (questions.stream().anyMatch(question -> !answers.containsKey(question.getId()))) {
            throw new BusinessRuleException("Answer every question before submitting");
        }

        int correctCount = (int) questions.stream()
                .filter(question -> isCorrect(question, answers.get(question.getId())))
                .count();
        int scorePercent = Math.round(correctCount * 100f / questions.size());
        boolean passed = scorePercent >= lesson.getQuizPassPercent();

        attemptRepository.save(QuizAttempt.builder()
                .studentId(student.getId())
                .lessonId(lessonId)
                .correctCount(correctCount)
                .totalCount(questions.size())
                .scorePercent(scorePercent)
                .passed(passed)
                .build());
        log.info("Quiz attempt lessonId={} studentId={} score={} passed={}", lessonId, student.getId(), scorePercent, passed);

        LessonProgressResponse progress = passed ? lessonProgressService.markComplete(email, lessonId) : null;
        return new QuizResultResponse(
                scorePercent,
                correctCount,
                questions.size(),
                lesson.getQuizPassPercent(),
                passed,
                questions.stream()
                        .map(question -> new QuizResultResponse.QuestionResult(
                                question.getId(),
                                isCorrect(question, answers.get(question.getId())),
                                passed ? correctOptionId(question) : null,
                                passed ? question.getExplanation() : null
                        ))
                        .toList(),
                progress
        );
    }

    @Override
    @Transactional(readOnly = true)
    public QuizAdminResponse findForAdmin(Integer lessonId) {
        return toAdminResponse(findQuizLesson(lessonId), questionRepository.findByLessonIdOrderByPositionAsc(lessonId));
    }

    @Override
    @Transactional
    public QuizAdminResponse save(Integer lessonId, QuizAdminRequest request) {
        Lesson lesson = findQuizLesson(lessonId);
        request.questions().forEach(question -> {
            if (question.options().stream().filter(QuizAdminRequest.Option::correct).count() != 1) {
                throw new BusinessRuleException("Each question needs exactly one correct option");
            }
        });

        // Removed first: the old rows must be gone before the new ones are numbered from 0
        questionRepository.deleteByLessonId(lessonId);
        questionRepository.flush();

        List<QuizQuestion> questions = new ArrayList<>();
        for (int i = 0; i < request.questions().size(); i++) {
            QuizAdminRequest.Question input = request.questions().get(i);
            QuizQuestion question = QuizQuestion.builder()
                    .lesson(lesson)
                    .position(i)
                    .prompt(input.prompt().trim())
                    .explanation(StringUtils.hasText(input.explanation()) ? input.explanation().trim() : null)
                    .build();
            for (int j = 0; j < input.options().size(); j++) {
                QuizAdminRequest.Option option = input.options().get(j);
                question.getOptions().add(QuizOption.builder()
                        .question(question)
                        .position(j)
                        .text(option.text().trim())
                        .correct(option.correct())
                        .build());
            }
            questions.add(question);
        }
        lesson.setQuizPassPercent(request.passPercent());
        List<QuizQuestion> saved = questionRepository.saveAll(questions);

        log.info("Saved quiz lessonId={} questions={}", lessonId, saved.size());
        return toAdminResponse(lesson, saved);
    }

    private boolean isCorrect(QuizQuestion question, Integer optionId) {
        return Objects.equals(correctOptionId(question), optionId);
    }

    private Integer correctOptionId(QuizQuestion question) {
        return question.getOptions().stream()
                .filter(QuizOption::isCorrect)
                .map(QuizOption::getId)
                .findFirst()
                .orElse(null);
    }

    private Lesson findQuizLesson(Integer lessonId) {
        Lesson lesson = lessonRepository.findDetailedById(lessonId)
                .orElseThrow(() -> ResourceNotFoundException.of("Lesson", lessonId));
        if (lesson.getType() != LessonType.QUIZ) {
            throw new BusinessRuleException("This lesson is not a quiz");
        }
        return lesson;
    }

    private Student requireEnrollment(String email, Lesson lesson) {
        Integer courseId = lesson.getSection().getCourse().getId();
        return learnerAccountService.findCurrentStudent(email)
                .filter(student -> studentCourseRepository.existsByStudentIdAndCourseId(student.getId(), courseId))
                .orElseThrow(() -> new ForbiddenActionException("You must be enrolled in this course to take its quiz"));
    }

    private QuizAdminResponse toAdminResponse(Lesson lesson, List<QuizQuestion> questions) {
        return new QuizAdminResponse(
                lesson.getId(),
                lesson.getQuizPassPercent(),
                questions.stream()
                        .map(question -> new QuizAdminResponse.Question(
                                question.getId(),
                                question.getPrompt(),
                                question.getExplanation(),
                                question.getOptions().stream()
                                        .map(option -> new QuizAdminResponse.Option(option.getId(), option.getText(), option.isCorrect()))
                                        .toList()
                        ))
                        .toList()
        );
    }
}
