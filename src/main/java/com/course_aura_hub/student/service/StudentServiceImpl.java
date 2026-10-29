package com.course_aura_hub.student.service;

import com.course_aura_hub.common.exception.DuplicateResourceException;
import com.course_aura_hub.common.exception.ResourceNotFoundException;
import com.course_aura_hub.student.dto.StudentCreateRequest;
import com.course_aura_hub.student.dto.StudentResponse;
import com.course_aura_hub.student.dto.StudentUpdateRequest;
import com.course_aura_hub.student.mapper.StudentAssembler;
import com.course_aura_hub.student.mapper.StudentMapper;
import com.course_aura_hub.student.model.Student;
import com.course_aura_hub.student.model.StudentStatus;
import com.course_aura_hub.student.repository.StudentRepository;
import com.course_aura_hub.student.repository.StudentSpecifications;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudentServiceImpl implements StudentService {

    private static final String DEFAULT_COUNTRY = "Global";
    private static final String DEFAULT_AVATAR_TEMPLATE =
            "https://api.dicebear.com/9.x/avataaars/svg?seed=%s";

    private final StudentRepository studentRepository;
    private final StudentMapper studentMapper;
    private final StudentAssembler studentAssembler;

    @Override
    @Transactional(readOnly = true)
    public List<StudentResponse> findAll(String status, String search) {
        StudentStatus parsedStatus = parseStatus(status);
        Specification<Student> specification = Specification.allOf(
                StudentSpecifications.status(parsedStatus),
                StudentSpecifications.search(search)
        );

        List<Student> students = studentRepository.findAll(
                specification,
                Sort.by(Sort.Direction.DESC, "joinedAt").and(Sort.by(Sort.Direction.DESC, "id"))
        );

        log.debug("Fetched {} students (status={}, search={})", students.size(), status, search);
        return students.stream().map(studentAssembler::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public StudentResponse findById(Integer id) {
        Student student = getStudent(id);
        return studentAssembler.toResponse(student);
    }

    @Override
    @Transactional
    public StudentResponse create(StudentCreateRequest request) {
        String email = normalizeEmail(request.email());
        if (studentRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException("A student with email " + email + " already exists");
        }

        Student student = studentMapper.toEntity(request);
        LocalDate today = LocalDate.now();
        student.setName(request.name().trim());
        student.setEmail(email);
        student.setAvatar(defaultText(request.avatar(), defaultAvatar(request.name())));
        student.setCountry(defaultText(request.country(), DEFAULT_COUNTRY));
        student.setStatus(StudentStatus.fromValue(request.status()));
        student.setTotalSpent(BigDecimal.ZERO);
        student.setJoinedAt(today);
        student.setLastActive(today);

        Student saved = studentRepository.save(student);
        log.info("Created student id={} email={} status={}", saved.getId(), saved.getEmail(), saved.getStatus());
        return studentAssembler.toResponse(saved);
    }

    @Override
    @Transactional
    public StudentResponse update(Integer id, StudentUpdateRequest request) {
        Student student = getStudent(id);

        if (request.name() != null && !StringUtils.hasText(request.name())) {
            throw new com.course_aura_hub.common.exception.BusinessRuleException("Student name cannot be blank");
        }

        if (request.email() != null) {
            String email = normalizeEmail(request.email());
            if (studentRepository.existsByEmailIgnoreCaseAndIdNot(email, id)) {
                throw new DuplicateResourceException("A student with email " + email + " already exists");
            }
        }

        studentMapper.updateEntity(request, student);

        if (request.name() != null) {
            student.setName(request.name().trim());
        }
        if (request.email() != null) {
            student.setEmail(normalizeEmail(request.email()));
        }
        if (request.status() != null) {
            student.setStatus(StudentStatus.fromValue(request.status()));
        }
        student.setLastActive(LocalDate.now());

        Student saved = studentRepository.save(student);
        log.info("Updated student id={} email={}", saved.getId(), saved.getEmail());
        return studentAssembler.toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(Integer id) {
        Student student = getStudent(id);
        studentRepository.delete(student);
        log.info("Deleted student id={} email={}", student.getId(), student.getEmail());
    }

    private Student getStudent(Integer id) {
        return studentRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Student", id));
    }

    private StudentStatus parseStatus(String status) {
        if (!StringUtils.hasText(status) || "all".equalsIgnoreCase(status)) {
            return null;
        }
        return StudentStatus.fromValue(status);
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String defaultAvatar(String name) {
        String seed = StringUtils.hasText(name) ? name.trim() : "student";
        return DEFAULT_AVATAR_TEMPLATE.formatted(URLEncoder.encode(seed, StandardCharsets.UTF_8));
    }
}
