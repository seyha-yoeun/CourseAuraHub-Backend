package com.course_aura_hub.config;

import com.course_aura_hub.auth.repository.UserRepository;
import com.course_aura_hub.course.model.Course;
import com.course_aura_hub.course.model.CourseLevel;
import com.course_aura_hub.course.model.CourseMonthlyMetric;
import com.course_aura_hub.course.model.CourseStatus;
import com.course_aura_hub.course.repository.CourseRepository;
import com.course_aura_hub.enrollment.model.Enrollment;
import com.course_aura_hub.enrollment.repository.EnrollmentRepository;
import com.course_aura_hub.settings.model.PlatformSettings;
import com.course_aura_hub.settings.repository.PlatformSettingsRepository;
import com.course_aura_hub.student.model.Student;
import com.course_aura_hub.student.model.StudentCourse;
import com.course_aura_hub.student.model.StudentStatus;
import com.course_aura_hub.student.repository.StudentCourseRepository;
import com.course_aura_hub.student.repository.StudentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Optional local/demo data loader.
 *
 * <p>The frontend mock dataset is bundled as a JSON resource and imported through the
 * real JPA layer only when {@code app.demo-data.enabled=true}. Existing rows are never
 * overwritten or deleted.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.demo-data.enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final int MONTHS_IN_YEAR = 12;
    private static final short SETTINGS_ID = 1;

    private final DemoDataProperties properties;
    private final ObjectMapper objectMapper;
    private final CourseRepository courseRepository;
    private final StudentRepository studentRepository;
    private final StudentCourseRepository studentCourseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final PlatformSettingsRepository platformSettingsRepository;
    private final UserRepository userRepository;

    private final Map<Integer, Course> courseByOriginalId = new LinkedHashMap<>();
    private final Map<Integer, Student> studentByOriginalId = new LinkedHashMap<>();

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        JsonNode root = readDemoData();
        seedCourses(root.path("courses"));
        seedStudents(root.path("students"));
        seedEnrollments(root.path("enrollments"));
        seedSettings(root.path("settings"));

        log.info(
                "Demo data seed complete courses={} students={} enrollments={} users={}",
                courseRepository.count(),
                studentRepository.count(),
                enrollmentRepository.count(),
                userRepository.count()
        );
    }

    private JsonNode readDemoData() {
        String file = properties.file();
        try (InputStream inputStream = new ClassPathResource(file).getInputStream()) {
            return objectMapper.readTree(inputStream);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read demo data file: " + file, ex);
        }
    }

    private void seedCourses(JsonNode coursesNode) {
        if (courseRepository.count() > 0) {
            for (JsonNode node : coursesNode) {
                courseRepository.findBySlug(text(node, "slug"))
                        .ifPresent(course -> courseByOriginalId.put(integer(node, "id"), course));
            }
            log.info("Skipping demo courses because courses already exist");
            return;
        }

        for (JsonNode node : coursesNode) {
            int originalId = integer(node, "id");
            Course course = Course.builder()
                    .slug(text(node, "slug"))
                    .title(text(node, "title"))
                    .description(text(node, "description"))
                    .instructor(text(node, "instructor"))
                    .price(decimal(node, "price"))
                    .originalPrice(decimal(node, "originalPrice"))
                    .status(CourseStatus.fromValue(text(node, "status")))
                    .category(text(node, "category"))
                    .level(CourseLevel.fromValue(text(node, "level")))
                    .thumbnail(text(node, "thumbnail"))
                    .rating(decimal(node, "rating"))
                    .totalStudents(integer(node, "totalStudents"))
                    .totalLessons(integer(node, "totalLessons"))
                    .totalHours(decimal(node, "totalHours"))
                    .revenue(decimal(node, "revenue"))
                    .completionRate(decimal(node, "completionRate"))
                    .createdAt(date(node, "createdAt"))
                    .updatedAt(date(node, "updatedAt"))
                    .tags(tags(node.path("tags")))
                    .build();

            JsonNode enrollments = node.path("monthlyEnrollments");
            JsonNode revenues = node.path("monthlyRevenue");
            for (short month = 1; month <= MONTHS_IN_YEAR; month++) {
                course.addMonthlyMetric(CourseMonthlyMetric.builder()
                        .month(month)
                        .enrollments(arrayInt(enrollments, month - 1))
                        .revenue(arrayDecimal(revenues, month - 1))
                        .build());
            }

            Course saved = courseRepository.save(course);
            courseByOriginalId.put(originalId, saved);
        }

        log.info("Seeded {} demo courses", coursesNode.size());
    }

    private void seedStudents(JsonNode studentsNode) {
        if (studentRepository.count() > 0) {
            for (JsonNode node : studentsNode) {
                studentRepository.findByEmailIgnoreCase(text(node, "email"))
                        .ifPresent(student -> studentByOriginalId.put(integer(node, "id"), student));
            }
            log.info("Skipping demo students because students already exist");
            return;
        }

        for (JsonNode node : studentsNode) {
            int originalId = integer(node, "id");
            Student student = Student.builder()
                    .name(text(node, "name"))
                    .email(text(node, "email"))
                    .avatar(text(node, "avatar"))
                    .totalSpent(decimal(node, "totalSpent"))
                    .joinedAt(date(node, "joinedAt"))
                    .lastActive(date(node, "lastActive"))
                    .status(StudentStatus.fromValue(text(node, "status")))
                    .country(text(node, "country"))
                    .build();
            Student saved = studentRepository.save(student);
            studentByOriginalId.put(originalId, saved);

            List<Integer> completedCourseIds = intList(node.path("completedCourses"));
            for (Integer originalCourseId : intList(node.path("enrolledCourses"))) {
                Course course = courseByOriginalId.get(originalCourseId);
                if (course == null) {
                    log.warn("Skipping demo enrollment link for missing courseId={}", originalCourseId);
                    continue;
                }

                StudentCourse studentCourse = StudentCourse.builder()
                        .student(saved)
                        .course(course)
                        .completed(completedCourseIds.contains(originalCourseId))
                        .build();
                studentCourseRepository.save(studentCourse);
            }
        }

        log.info("Seeded {} demo students", studentsNode.size());
    }

    private void seedEnrollments(JsonNode enrollmentsNode) {
        if (enrollmentRepository.count() > 0) {
            log.info("Skipping demo enrollments because enrollments already exist");
            return;
        }

        for (JsonNode node : enrollmentsNode) {
            Student student = studentByOriginalId.get(integer(node, "studentId"));
            Course course = courseByOriginalId.get(integer(node, "courseId"));
            if (student == null || course == null) {
                log.warn(
                        "Skipping demo enrollment for studentId={} courseId={}",
                        integer(node, "studentId"),
                        integer(node, "courseId")
                );
                continue;
            }

            Enrollment enrollment = Enrollment.builder()
                    .student(student)
                    .course(course)
                    .enrolledAt(date(node, "enrolledAt"))
                    .amount(decimal(node, "amount"))
                    .build();
            enrollmentRepository.save(enrollment);
        }

        log.info("Seeded {} demo enrollment ledger rows", enrollmentsNode.size());
    }

    private void seedSettings(JsonNode settingsNode) {
        if (settingsNode == null || settingsNode.isMissingNode()) {
            return;
        }

        PlatformSettings settings = platformSettingsRepository.findById(SETTINGS_ID)
                .orElseGet(() -> PlatformSettings.builder()
                        .id(SETTINGS_ID)
                        .platformName("Course Aura Hub")
                        .platformTagline("Learn. Build. Grow.")
                        .currency("USD")
                        .defaultLanguage("EN")
                        .notifications(new LinkedHashMap<>())
                        .profile(new LinkedHashMap<>())
                        .build());

        settings.setPlatformName(text(settingsNode, "platformName"));
        settings.setPlatformTagline(text(settingsNode, "platformTagline"));
        settings.setCurrency(text(settingsNode, "currency"));
        settings.setDefaultLanguage(text(settingsNode, "defaultLanguage"));
        settings.setNotifications(booleanMap(settingsNode.path("notifications")));

        if (settings.getProfile() == null) {
            settings.setProfile(new LinkedHashMap<>());
        }
        platformSettingsRepository.save(settings);
    }

    private List<String> tags(JsonNode node) {
        List<String> tags = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(value -> tags.add(value.asText()));
        }
        return tags;
    }

    private List<Integer> intList(JsonNode node) {
        List<Integer> values = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(value -> values.add(value.asInt()));
        }
        return values;
    }

    private Map<String, Boolean> booleanMap(JsonNode node) {
        Map<String, Boolean> values = new LinkedHashMap<>();
        if (node != null && node.isObject()) {
            node.fields().forEachRemaining(entry -> values.put(entry.getKey(), entry.getValue().asBoolean()));
        }
        return values;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText();
    }

    private int integer(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? 0 : value.asInt();
    }

    private BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? BigDecimal.ZERO : value.decimalValue();
    }

    private LocalDate date(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? LocalDate.now() : LocalDate.parse(value.asText());
    }

    private int arrayInt(JsonNode node, int index) {
        return node != null && node.isArray() && node.size() > index ? node.get(index).asInt() : 0;
    }

    private BigDecimal arrayDecimal(JsonNode node, int index) {
        return node != null && node.isArray() && node.size() > index
                ? node.get(index).decimalValue()
                : BigDecimal.ZERO;
    }
}
