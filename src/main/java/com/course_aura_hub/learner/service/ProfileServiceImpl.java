package com.course_aura_hub.learner.service;

import com.course_aura_hub.auth.model.User;
import com.course_aura_hub.auth.repository.UserRepository;
import com.course_aura_hub.common.exception.BusinessRuleException;
import com.course_aura_hub.common.exception.ResourceNotFoundException;
import com.course_aura_hub.file.service.FileService;
import com.course_aura_hub.learner.dto.LearnerProfileResponse;
import com.course_aura_hub.learner.dto.LearnerProfileUpdateRequest;
import com.course_aura_hub.student.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final FileService fileService;

    @Override
    @Transactional(readOnly = true)
    public LearnerProfileResponse getProfile(String email) {
        return toResponse(findUser(email));
    }

    @Override
    @Transactional
    public LearnerProfileResponse updateProfile(String email, LearnerProfileUpdateRequest request) {
        User user = findUser(email);

        if (request.firstName() != null) {
            user.setFirstName(requireText(request.firstName(), "First name"));
        }
        if (request.lastName() != null) {
            user.setLastName(requireText(request.lastName(), "Last name"));
        }
        if (request.imageUrl() != null) {
            String previousImage = user.getImageUrl();
            user.setImageUrl(trimToNull(request.imageUrl()));
            if (!Objects.equals(previousImage, user.getImageUrl())) {
                fileService.release(previousImage, user.getId());
            }
        }
        if (request.bio() != null) {
            user.setBio(trimToNull(request.bio()));
        }
        if (request.website() != null) {
            user.setWebsite(trimToNull(request.website()));
        }
        if (request.twitter() != null) {
            user.setTwitter(trimToNull(request.twitter()));
        }

        // Keep the learner record's public name and avatar (shown on reviews) in sync
        studentRepository.findByUserId(user.getId()).ifPresent(student -> {
            student.setName(LearnerAccountServiceImpl.displayName(user));
            student.setAvatar(StringUtils.hasText(user.getImageUrl()) ? user.getImageUrl() : "");
        });

        log.info("Updated profile for user email={}", user.getEmail());
        return toResponse(user);
    }

    private User findUser(String email) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        return userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> ResourceNotFoundException.of("User", normalizedEmail));
    }

    private LearnerProfileResponse toResponse(User user) {
        return new LearnerProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getImageUrl(),
                user.getBio(),
                user.getWebsite(),
                user.getTwitter(),
                user.getProvider().name(),
                StringUtils.hasText(user.getPassword())
        );
    }

    private String requireText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessRuleException(field + " cannot be blank");
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
