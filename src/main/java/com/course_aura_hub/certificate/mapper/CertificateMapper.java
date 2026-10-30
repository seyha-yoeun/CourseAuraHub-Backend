package com.course_aura_hub.certificate.mapper;

import com.course_aura_hub.certificate.dto.CertificateResponse;
import com.course_aura_hub.certificate.dto.CertificateVerificationResponse;
import com.course_aura_hub.certificate.model.CourseCertificate;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CertificateMapper {

    @Mapping(target = "courseSlug", source = "course.slug")
    @Mapping(target = "courseTitle", source = "course.title")
    @Mapping(target = "instructor", source = "course.instructor")
    @Mapping(target = "thumbnail", source = "course.thumbnail")
    CertificateResponse toResponse(CourseCertificate certificate);

    @Mapping(target = "learnerName", source = "student.name")
    @Mapping(target = "courseTitle", source = "course.title")
    @Mapping(target = "instructor", source = "course.instructor")
    CertificateVerificationResponse toVerificationResponse(CourseCertificate certificate);
}
