package com.course_aura_hub.survey.service;

import com.course_aura_hub.common.exception.BusinessRuleException;
import com.course_aura_hub.common.exception.DuplicateResourceException;
import com.course_aura_hub.common.exception.ForbiddenActionException;
import com.course_aura_hub.common.exception.ResourceNotFoundException;
import com.course_aura_hub.common.security.Actor;
import com.course_aura_hub.course.model.Course;
import com.course_aura_hub.course.service.CourseAccess;
import com.course_aura_hub.curriculum.repository.LessonProgressRepository;
import com.course_aura_hub.curriculum.repository.LessonRepository;
import com.course_aura_hub.curriculum.service.LessonCompleted;
import com.course_aura_hub.learner.service.LearnerAccountService;
import com.course_aura_hub.notification.model.NotificationType;
import com.course_aura_hub.notification.service.NotificationService;
import com.course_aura_hub.student.model.Student;
import com.course_aura_hub.student.repository.StudentCourseRepository;
import com.course_aura_hub.survey.dto.SurveyDtos;
import com.course_aura_hub.survey.model.CourseSurvey;
import com.course_aura_hub.survey.model.SurveyQuestion;
import com.course_aura_hub.survey.model.SurveyResponse;
import com.course_aura_hub.survey.model.SurveyTrigger;
import com.course_aura_hub.survey.repository.CourseSurveyRepository;
import com.course_aura_hub.survey.repository.SurveyResponseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class SurveyServiceImpl implements SurveyService {

    static final String RECOMMEND_QUESTION = "How likely are you to recommend this course to a friend? (0-10)";
    private static final Set<String> TYPES = Set.of("rating", "choice", "text");
    private static final int MAX_TEXT_ANSWER = 2000;
    private static final int COMMENTS_SHOWN = 20;

    private final CourseSurveyRepository surveyRepository;
    private final SurveyResponseRepository responseRepository;
    private final CourseAccess courseAccess;
    private final LearnerAccountService learnerAccountService;
    private final StudentCourseRepository studentCourseRepository;
    private final LessonRepository lessonRepository;
    private final LessonProgressRepository lessonProgressRepository;
    private final NotificationService notificationService;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(readOnly = true)
    public SurveyDtos.SurveyView findForManager(Actor actor, String courseSlug) {
        Course course = managed(actor, courseSlug);
        return surveyRepository.findByCourseId(course.getId()).map(survey -> toView(course, survey)).orElse(null);
    }

    @Override
    @Transactional
    public SurveyDtos.SurveyView save(Actor actor, String courseSlug, SurveyDtos.SurveyRequest request) {
        Course course = managed(actor, courseSlug);
        SurveyTrigger trigger = parseTrigger(request.trigger());
        List<SurveyQuestion> questions = new ArrayList<>();
        for (int i = 0; i < request.questions().size(); i++) {
            questions.add(toQuestion("q" + (i + 1), request.questions().get(i)));
        }

        CourseSurvey survey = surveyRepository.findByCourseId(course.getId())
                .orElseGet(() -> CourseSurvey.builder().courseId(course.getId()).build());
        survey.setTrigger(trigger);
        survey.setQuestions(questions);
        if (request.active() != null) {
            survey.setActive(request.active());
        }
        return toView(course, surveyRepository.save(survey));
    }

    @Override
    @Transactional
    public void delete(Actor actor, String courseSlug) {
        Course course = managed(actor, courseSlug);
        surveyRepository.findByCourseId(course.getId()).ifPresent(surveyRepository::delete);
    }

    @Override
    @Transactional(readOnly = true)
    public SurveyDtos.Results results(Actor actor, String courseSlug) {
        Course course = managed(actor, courseSlug);
        CourseSurvey survey = surveyRepository.findByCourseId(course.getId())
                .orElseThrow(() -> new ResourceNotFoundException("This course has no survey"));
        List<SurveyResponse> responses = responseRepository.findBySurveyIdOrderByCreatedAtDescIdDesc(survey.getId());

        List<SurveyDtos.QuestionResult> questions = survey.getQuestions().stream()
                .map(question -> questionResult(question, responses))
                .toList();
        return new SurveyDtos.Results(course.getSlug(), responses.size(), nps(responses), questions);
    }

    @Override
    @Transactional(readOnly = true)
    public SurveyDtos.LearnerSurvey findForLearner(String email, String courseSlug) {
        Course course = courseAccess.requireCourse(courseSlug);
        Student student = enrolled(email, course);
        CourseSurvey survey = activeSurvey(course);
        return new SurveyDtos.LearnerSurvey(toView(course, survey), eligible(survey, student.getId(), course.getId()),
                responseRepository.existsBySurveyIdAndStudentId(survey.getId(), student.getId()));
    }

    @Override
    @Transactional
    public void answer(String email, String courseSlug, SurveyDtos.AnswerRequest request) {
        Course course = courseAccess.requireCourse(courseSlug);
        Student student = enrolled(email, course);
        CourseSurvey survey = activeSurvey(course);
        if (!eligible(survey, student.getId(), course.getId())) {
            throw new BusinessRuleException(survey.getTrigger() == SurveyTrigger.COMPLETION
                    ? "Finish the course to answer its survey" : "Complete half the course to answer its survey");
        }
        if (responseRepository.existsBySurveyIdAndStudentId(survey.getId(), student.getId())) {
            throw new DuplicateResourceException("You have already answered this survey");
        }
        responseRepository.save(SurveyResponse.builder()
                .surveyId(survey.getId())
                .studentId(student.getId())
                .npsScore(request.npsScore())
                .answers(validateAnswers(survey, request.answers() == null ? Map.of() : request.answers()))
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public SurveyDtos.Feedback feedback(Integer courseId) {
        return surveyRepository.findByCourseId(courseId)
                .map(survey -> {
                    List<SurveyResponse> responses = responseRepository.findBySurveyIdOrderByCreatedAtDescIdDesc(survey.getId());
                    return new SurveyDtos.Feedback(responses.size(), nps(responses).score());
                })
                .orElse(new SurveyDtos.Feedback(0, null));
    }

    /** Asks the learner once, the first time their progress reaches the survey's point. */
    @EventListener
    @Transactional
    public void onLessonCompleted(LessonCompleted event) {
        surveyRepository.findByCourseId(event.courseId())
                .filter(CourseSurvey::isActive)
                .filter(survey -> reached(survey.getTrigger(), event.completedLessons(), event.totalLessons()))
                .ifPresent(survey -> {
                    int inserted = jdbcTemplate.update("""
                            insert into survey_prompts (survey_id, student_id) values (?, ?) on conflict do nothing
                            """, survey.getId(), event.studentId());
                    if (inserted == 1 && !responseRepository.existsBySurveyIdAndStudentId(survey.getId(), event.studentId())) {
                        String slug = jdbcTemplate.queryForObject("select slug from courses where id = ?", String.class,
                                event.courseId());
                        notificationService.notifyStudent(event.studentId(), NotificationType.COURSE_SURVEY,
                                "How is the course going?", "Two minutes of feedback helps us improve it.",
                                "/learn/" + slug + "?survey=1");
                    }
                });
    }

    private boolean eligible(CourseSurvey survey, Integer studentId, Integer courseId) {
        return reached(survey.getTrigger(), lessonProgressRepository.countCompleted(studentId, courseId),
                lessonRepository.countByCourseId(courseId));
    }

    private static boolean reached(SurveyTrigger trigger, long done, long total) {
        if (total <= 0) {
            return false;
        }
        return trigger == SurveyTrigger.COMPLETION ? done >= total : done * 2 >= total;
    }

    private Map<String, Object> validateAnswers(CourseSurvey survey, Map<String, Object> answers) {
        Map<String, Object> clean = new LinkedHashMap<>();
        Set<String> known = new HashSet<>();
        for (SurveyQuestion question : survey.getQuestions()) {
            known.add(question.id());
            Object value = answers.get(question.id());
            boolean blank = value == null || (value instanceof String text && !StringUtils.hasText(text));
            if (blank) {
                if (question.required()) {
                    throw new BusinessRuleException("Please answer: " + question.text());
                }
                continue;
            }
            clean.put(question.id(), switch (question.type()) {
                case "rating" -> {
                    if (!(value instanceof Number number) || number.doubleValue() != number.intValue()
                            || number.intValue() < 1 || number.intValue() > 5) {
                        throw new BusinessRuleException("Rate from 1 to 5: " + question.text());
                    }
                    yield number.intValue();
                }
                case "choice" -> {
                    if (!(value instanceof String choice) || !question.options().contains(choice)) {
                        throw new BusinessRuleException("Pick one of the options: " + question.text());
                    }
                    yield choice;
                }
                default -> {
                    String text = String.valueOf(value).trim();
                    if (text.length() > MAX_TEXT_ANSWER) {
                        throw new BusinessRuleException("Answers must not exceed " + MAX_TEXT_ANSWER + " characters");
                    }
                    yield text;
                }
            });
        }
        answers.keySet().stream().filter(key -> !known.contains(key)).findFirst().ifPresent(key -> {
            throw new BusinessRuleException("Unknown question: " + key);
        });
        return clean;
    }

    private static SurveyDtos.QuestionResult questionResult(SurveyQuestion question, List<SurveyResponse> responses) {
        List<Object> values = responses.stream()
                .map(response -> response.getAnswers().get(question.id()))
                .filter(value -> value != null)
                .toList();
        Map<String, Long> distribution = new LinkedHashMap<>();
        BigDecimal average = null;
        List<String> comments = List.of();
        switch (question.type()) {
            case "rating" -> {
                for (int rating = 1; rating <= 5; rating++) {
                    distribution.put(String.valueOf(rating), 0L);
                }
                values.forEach(value -> distribution.merge(String.valueOf(((Number) value).intValue()), 1L, Long::sum));
                if (!values.isEmpty()) {
                    double mean = values.stream().mapToInt(value -> ((Number) value).intValue()).average().orElse(0);
                    average = BigDecimal.valueOf(mean).setScale(2, RoundingMode.HALF_UP);
                }
            }
            case "choice" -> {
                question.options().forEach(option -> distribution.put(option, 0L));
                values.forEach(value -> distribution.merge(String.valueOf(value), 1L, Long::sum));
            }
            default -> comments = values.stream().map(String::valueOf).limit(COMMENTS_SHOWN).toList();
        }
        return new SurveyDtos.QuestionResult(question.id(), question.text(), question.type(), values.size(), average,
                distribution, comments);
    }

    /** Promoters answer 9-10, detractors 0-6; the score is the difference in percentage points. */
    private static SurveyDtos.Nps nps(List<SurveyResponse> responses) {
        long promoters = responses.stream().filter(response -> response.getNpsScore() >= 9).count();
        long detractors = responses.stream().filter(response -> response.getNpsScore() <= 6).count();
        long passives = responses.size() - promoters - detractors;
        Integer score = responses.isEmpty() ? null
                : (int) Math.round(100.0 * (promoters - detractors) / responses.size());
        return new SurveyDtos.Nps(score, promoters, passives, detractors);
    }

    private static SurveyQuestion toQuestion(String id, SurveyDtos.QuestionRequest request) {
        String type = request.type().trim().toLowerCase(Locale.ROOT);
        if (!TYPES.contains(type)) {
            throw new BusinessRuleException("Question type must be rating, choice or text");
        }
        List<String> options = List.of();
        if ("choice".equals(type)) {
            options = request.options() == null ? List.of()
                    : request.options().stream().map(String::trim).distinct().toList();
            if (options.size() < 2) {
                throw new BusinessRuleException("A choice question needs at least 2 different options");
            }
        }
        return new SurveyQuestion(id, request.text().trim(), type, options, request.required());
    }

    private Course managed(Actor actor, String slug) {
        Course course = courseAccess.requireCourse(slug);
        courseAccess.requireManager(course, actor);
        return course;
    }

    /**
     * Only looks the learner up: currentStudent would insert a student row on first use, which a read-only
     * transaction (findForLearner) rejects. Someone without a learner record isn't enrolled anyway.
     */
    private Student enrolled(String email, Course course) {
        return learnerAccountService.findCurrentStudent(email)
                .filter(student -> studentCourseRepository.findByStudentIdAndCourseId(student.getId(), course.getId()).isPresent())
                .orElseThrow(() -> new ForbiddenActionException("Enroll in this course to see its survey"));
    }

    private CourseSurvey activeSurvey(Course course) {
        return surveyRepository.findByCourseId(course.getId())
                .filter(CourseSurvey::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("This course has no survey"));
    }

    private static SurveyDtos.SurveyView toView(Course course, CourseSurvey survey) {
        return new SurveyDtos.SurveyView(
                course.getSlug(),
                survey.getTrigger().name().toLowerCase(Locale.ROOT),
                survey.isActive(),
                RECOMMEND_QUESTION,
                survey.getQuestions().stream()
                        .map(question -> new SurveyDtos.Question(question.id(), question.text(), question.type(),
                                question.options(), question.required()))
                        .toList()
        );
    }

    private static SurveyTrigger parseTrigger(String value) {
        try {
            return SurveyTrigger.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("trigger must be percent_50 or completion");
        }
    }
}
