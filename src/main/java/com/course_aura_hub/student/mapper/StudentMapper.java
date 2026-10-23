package com.course_aura_hub.student.mapper;

import com.course_aura_hub.student.dto.StudentCreateRequest;
import com.course_aura_hub.student.dto.StudentUpdateRequest;
import com.course_aura_hub.student.model.Student;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface StudentMapper {

    @Mapping(target = "status", ignore = true)
    Student toEntity(StudentCreateRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "status", ignore = true)
    void updateEntity(StudentUpdateRequest request, @MappingTarget Student student);
}
