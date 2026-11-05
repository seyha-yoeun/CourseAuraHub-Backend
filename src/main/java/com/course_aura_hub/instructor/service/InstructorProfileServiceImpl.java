package com.course_aura_hub.instructor.service;

import com.course_aura_hub.auth.model.Role;
import com.course_aura_hub.auth.model.User;
import com.course_aura_hub.auth.repository.UserRepository;
import com.course_aura_hub.common.exception.DuplicateResourceException;
import com.course_aura_hub.common.exception.ResourceNotFoundException;
import com.course_aura_hub.common.security.Actor;
import com.course_aura_hub.course.dto.CourseResponse;
import com.course_aura_hub.course.mapper.CourseAssembler;
import com.course_aura_hub.course.model.Course;
import com.course_aura_hub.course.model.CourseStatus;
import com.course_aura_hub.course.repository.CourseRepository;
import com.course_aura_hub.instructor.dto.InstructorCardResponse;
import com.course_aura_hub.instructor.dto.InstructorProfileRequest;
import com.course_aura_hub.instructor.dto.InstructorProfileResponse;
import com.course_aura_hub.instructor.dto.PublicInstructorResponse;
import com.course_aura_hub.instructor.model.InstructorProfile;
import com.course_aura_hub.instructor.repository.InstructorProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InstructorProfileServiceImpl implements InstructorProfileService {

    private static final int DEFAULT_LIST_SIZE = 20;
    private static final int MAX_LIST_SIZE = 50;
    private static final int MAX_HANDLE_LENGTH = 40;
    private static final String FALLBACK_HANDLE = "instructor";

    private final InstructorProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final CourseAssembler courseAssembler;

    @Override
    @Transactional
    public InstructorProfileResponse findMine(Actor actor) {
        return toOwnResponse(profileFor(actor.userId()));
    }

    @Override
    @Transactional
    public InstructorProfileResponse updateMine(Actor actor, InstructorProfileRequest request) {
        InstructorProfile profile = profileFor(actor.userId());

        if (request.handle() != null && !request.handle().equals(profile.getHandle())) {
            if (profileRepository.existsByHandle(request.handle())) {
                throw new DuplicateResourceException("That handle is already taken");
            }
            profile.setHandle(request.handle());
        }
        if (request.headline() != null) {
            profile.setHeadline(blankToNull(request.headline()));
        }
        if (request.linkedinUrl() != null) {
            profile.setLinkedinUrl(blankToNull(request.linkedinUrl()));
        }
        if (request.githubUrl() != null) {
            profile.setGithubUrl(blankToNull(request.githubUrl()));
        }
        if (request.youtubeUrl() != null) {
            profile.setYoutubeUrl(blankToNull(request.youtubeUrl()));
        }
        log.info("Instructor profile updated userId={} handle={}", actor.userId(), profile.getHandle());
        return toOwnResponse(profile);
    }

    @Override
    @Transactional(readOnly = true)
    public PublicInstructorResponse findByHandle(String handle) {
        InstructorProfile profile = profileRepository.findByHandle(handle.toLowerCase(Locale.ROOT))
                .orElseThrow(() -> ResourceNotFoundException.of("Instructor", handle));
        User user = teachingUser(profile.getUserId())
                .orElseThrow(() -> ResourceNotFoundException.of("Instructor", handle));

        List<Course> courses = publishedCourses(user.getId());
        List<CourseResponse> courseResponses = courses.stream()
                .map(course -> courseAssembler.toPublicResponse(course, null))
                .toList();

        return new PublicInstructorResponse(
                profile.getHandle(),
                fullName(user),
                profile.getHeadline(),
                user.getBio(),
                user.getImageUrl(),
                new PublicInstructorResponse.Links(
                        user.getWebsite(), user.getTwitter(),
                        profile.getLinkedinUrl(), profile.getGithubUrl(), profile.getYoutubeUrl()),
                new PublicInstructorResponse.Stats(
                        courses.size(),
                        totalStudents(courses),
                        averageRating(courses),
                        profileRepository.countPublishedReviews(user.getId())),
                courseResponses
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<InstructorCardResponse> findListed(Integer limit) {
        int size = limit == null ? DEFAULT_LIST_SIZE : Math.max(1, Math.min(limit, MAX_LIST_SIZE));
        return profileRepository.findListed(size).stream()
                .map(this::toCard)
                .flatMap(Optional::stream)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public InstructorCardResponse findForCourse(String courseSlug) {
        Course course = courseRepository.findBySlug(courseSlug)
                .filter(found -> found.getStatus() == CourseStatus.PUBLISHED)
                .orElseThrow(() -> ResourceNotFoundException.of("Course", courseSlug));
        if (course.getOwnerId() == null) {
            throw ResourceNotFoundException.of("Instructor profile", courseSlug);
        }
        return profileRepository.findById(course.getOwnerId())
                .flatMap(this::toCard)
                .orElseThrow(() -> ResourceNotFoundException.of("Instructor profile", courseSlug));
    }

    private Optional<InstructorCardResponse> toCard(InstructorProfile profile) {
        return teachingUser(profile.getUserId()).map(user -> {
            List<Course> courses = publishedCourses(user.getId());
            return new InstructorCardResponse(
                    profile.getHandle(),
                    fullName(user),
                    profile.getHeadline(),
                    user.getImageUrl(),
                    courses.size(),
                    totalStudents(courses)
            );
        });
    }

    private InstructorProfile profileFor(UUID userId) {
        return profileRepository.findById(userId).orElseGet(() -> {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> ResourceNotFoundException.of("User", userId));
            InstructorProfile created = profileRepository.save(InstructorProfile.builder()
                    .userId(userId)
                    .handle(uniqueHandle(fullName(user)))
                    .build());
            log.info("Instructor profile created userId={} handle={}", userId, created.getHandle());
            return created;
        });
    }

    /** A profile is public only while its account is enabled and allowed to teach. */
    private Optional<User> teachingUser(UUID userId) {
        return userRepository.findById(userId)
                .filter(User::isEnabled)
                .filter(user -> user.getRole() == Role.ROLE_INSTRUCTOR || user.getRole() == Role.ROLE_ADMIN);
    }

    private List<Course> publishedCourses(UUID ownerId) {
        return courseRepository.findByOwnerIdOrderByCreatedAtDescIdDesc(ownerId).stream()
                .filter(course -> course.getStatus() == CourseStatus.PUBLISHED)
                .toList();
    }

    private String uniqueHandle(String name) {
        String base = Normalizer.normalize(name, Normalizer.Form.NFD)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s-]", "")
                .trim()
                .replaceAll("[\\s-]+", "-")
                .replaceAll("(^-|-$)", "");
        if (base.length() < 3) {
            base = FALLBACK_HANDLE;
        }
        if (base.length() > MAX_HANDLE_LENGTH - 4) {
            base = base.substring(0, MAX_HANDLE_LENGTH - 4).replaceAll("-$", "");
        }
        String candidate = base;
        int suffix = 2;
        while (profileRepository.existsByHandle(candidate)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private static InstructorProfileResponse toOwnResponse(InstructorProfile profile) {
        return new InstructorProfileResponse(
                profile.getHandle(),
                profile.getHeadline(),
                profile.getLinkedinUrl(),
                profile.getGithubUrl(),
                profile.getYoutubeUrl(),
                "/instructors/" + profile.getHandle()
        );
    }

    private static long totalStudents(List<Course> courses) {
        return courses.stream().map(Course::getTotalStudents).filter(Objects::nonNull).mapToLong(Integer::longValue).sum();
    }

    /** Average over rated courses only, so a new course doesn't drag the score to zero. */
    private static BigDecimal averageRating(List<Course> courses) {
        List<BigDecimal> rated = courses.stream()
                .map(Course::getRating)
                .filter(rating -> rating != null && rating.signum() > 0)
                .toList();
        if (rated.isEmpty()) {
            return BigDecimal.ZERO.setScale(2);
        }
        return rated.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(rated.size()), 2, RoundingMode.HALF_UP);
    }

    private static String fullName(User user) {
        String first = user.getFirstName() == null ? "" : user.getFirstName().trim();
        String last = user.getLastName() == null ? "" : user.getLastName().trim();
        return (first + " " + last).trim();
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
