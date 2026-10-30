package com.course_aura_hub.learner.service;

import com.course_aura_hub.auth.model.User;
import com.course_aura_hub.auth.repository.UserRepository;
import com.course_aura_hub.common.exception.ResourceNotFoundException;
import com.course_aura_hub.student.model.Student;
import com.course_aura_hub.student.model.StudentStatus;
import com.course_aura_hub.student.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LearnerAccountServiceImpl implements LearnerAccountService {

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;

    @Override
    @Transactional
    public Student currentStudent(String email) {
        User user = findUser(email);

        Optional<Student> linked = studentRepository.findByUserId(user.getId());
        if (linked.isPresent()) {
            return linked.get();
        }

        Optional<Student> byEmail = studentRepository.findByEmailIgnoreCase(user.getEmail());
        if (byEmail.isPresent()) {
            Student student = byEmail.get();
            if (student.getUser() == null) {
                student.setUser(user);
                log.info("Linked existing student id={} to user email={}", student.getId(), user.getEmail());
            }
            return student;
        }

        Student student = studentRepository.save(Student.builder()
                .name(displayName(user))
                .email(user.getEmail())
                .avatar(StringUtils.hasText(user.getImageUrl()) ? user.getImageUrl() : "")
                .totalSpent(BigDecimal.ZERO)
                .joinedAt(LocalDate.now())
                .lastActive(LocalDate.now())
                .status(StudentStatus.ACTIVE)
                .country("")
                .user(user)
                .build());
        log.info("Created learner student id={} for user email={}", student.getId(), user.getEmail());
        return student;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Student> findCurrentStudent(String email) {
        User user = findUser(email);
        return studentRepository.findByUserId(user.getId())
                .or(() -> studentRepository.findByEmailIgnoreCase(user.getEmail())
                        .filter(student -> student.getUser() == null));
    }

    private User findUser(String email) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        return userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> ResourceNotFoundException.of("User", normalizedEmail));
    }

    static String displayName(User user) {
        String name = (nullToEmpty(user.getFirstName()) + " " + nullToEmpty(user.getLastName())).trim();
        return name.isEmpty() ? user.getEmail() : name;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
