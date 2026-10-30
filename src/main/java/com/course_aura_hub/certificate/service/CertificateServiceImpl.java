package com.course_aura_hub.certificate.service;

import com.course_aura_hub.certificate.dto.CertificateResponse;
import com.course_aura_hub.certificate.dto.CertificateVerificationResponse;
import com.course_aura_hub.certificate.mapper.CertificateMapper;
import com.course_aura_hub.certificate.repository.CourseCertificateRepository;
import com.course_aura_hub.common.exception.ResourceNotFoundException;
import com.course_aura_hub.learner.service.LearnerAccountService;
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

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean issueIfAbsent(Integer studentId, Integer courseId) {
        if (certificateRepository.existsByStudentIdAndCourseId(studentId, courseId)) {
            return false;
        }
        boolean issued = certificateRepository.insertIfAbsent(numberGenerator.next(), studentId, courseId) > 0;
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
            issued += certificateRepository.insertIfAbsent(numberGenerator.next(), studentId, courseId);
        }
        if (issued > 0) {
            log.info("Issued course certificates after curriculum change courseId={} count={}", courseId, issued);
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
}
