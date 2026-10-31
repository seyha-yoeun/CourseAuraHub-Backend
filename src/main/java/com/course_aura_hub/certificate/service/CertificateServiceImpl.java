package com.course_aura_hub.certificate.service;

import com.course_aura_hub.certificate.dto.CertificateResponse;
import com.course_aura_hub.certificate.dto.CertificateVerificationResponse;
import com.course_aura_hub.certificate.mapper.CertificateMapper;
import com.course_aura_hub.certificate.repository.CourseCertificateRepository;
import com.course_aura_hub.common.exception.ResourceNotFoundException;
import com.course_aura_hub.course.model.Course;
import com.course_aura_hub.course.repository.CourseRepository;
import com.course_aura_hub.learner.service.LearnerAccountService;
import com.course_aura_hub.notification.model.NotificationType;
import com.course_aura_hub.notification.service.NotificationService;
import com.course_aura_hub.settings.service.SettingsService;
import com.course_aura_hub.student.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CertificateServiceImpl implements CertificateService {

    private final CourseCertificateRepository certificateRepository;
    private final CertificateNumberGenerator numberGenerator;
    private final CertificateMapper certificateMapper;
    private final LearnerAccountService learnerAccountService;
    private final CourseRepository courseRepository;
    private final StudentRepository studentRepository;
    private final NotificationService notificationService;
    private final CertificatePdfRenderer pdfRenderer;
    private final SettingsService settingsService;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean issueIfAbsent(Integer studentId, Integer courseId) {
        if (certificateRepository.existsByStudentIdAndCourseId(studentId, courseId)) {
            return false;
        }
        boolean issued = issue(studentId, courseId);
        if (issued) {
            log.info("Issued course certificate studentId={} courseId={}", studentId, courseId);
        }
        return issued;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public int issueForCompletedLearners(Integer courseId) {
        int issued = 0;
        for (Integer studentId : certificateRepository.findCompletedStudentIdsWithoutCertificate(courseId)) {
            if (issue(studentId, courseId)) {
                issued++;
            }
        }
        if (issued > 0) {
            log.info("Issued course certificates after curriculum change courseId={} count={}", courseId, issued);
        }
        return issued;
    }

    private boolean issue(Integer studentId, Integer courseId) {
        String number = numberGenerator.next();
        Course course = courseRepository.findById(courseId).orElseThrow();
        String learnerName = studentRepository.findById(studentId).orElseThrow().getName();
        boolean issued = certificateRepository.insertIfAbsent(
                number, studentId, courseId, learnerName, course.getTitle(), course.getInstructor()) > 0;
        if (issued) {
            notificationService.notifyStudent(
                    studentId,
                    NotificationType.CERTIFICATE_ISSUED,
                    "Certificate earned",
                    "You completed " + course.getTitle() + ". Your certificate is ready.",
                    "/certificates/" + number
            );
            if (settingsService.isCourseCompletionAlertEnabled()) {
                notificationService.notifyAdmins(
                        NotificationType.COURSE_COMPLETED,
                        "Course completed",
                        learnerName + " completed " + course.getTitle() + ".",
                        "/courses/" + course.getSlug()
                );
            }
        }
        return issued;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CertificateResponse> findMine(String email) {
        return learnerAccountService.findCurrentStudent(email)
                .map(student -> certificateRepository.findByStudentIdOrderByIssuedAtDesc(student.getId()).stream()
                        .map(certificateMapper::toResponse)
                        .toList())
                .orElse(List.of());
    }

    @Override
    @Transactional(readOnly = true)
    public CertificateVerificationResponse verify(String certificateNumber) {
        return Optional.ofNullable(CertificateNumberGenerator.normalize(certificateNumber))
                .flatMap(certificateRepository::findByCertificateNumber)
                .map(certificateMapper::toVerificationResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Certificate not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] renderPdf(String email, String certificateNumber, String language) {
        return learnerAccountService.findCurrentStudent(email)
                .flatMap(student -> Optional.ofNullable(CertificateNumberGenerator.normalize(certificateNumber))
                        .flatMap(certificateRepository::findByCertificateNumber)
                        .filter(certificate -> certificate.getStudent().getId().equals(student.getId())))
                .map(certificate -> pdfRenderer.render(certificate, language))
                .orElseThrow(() -> new ResourceNotFoundException("Certificate not found"));
    }
}
