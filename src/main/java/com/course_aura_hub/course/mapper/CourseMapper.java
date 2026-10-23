package com.course_aura_hub.course.mapper;

import com.course_aura_hub.course.dto.CourseCreateRequest;
import com.course_aura_hub.course.dto.CourseUpdateRequest;
import com.course_aura_hub.course.model.Course;
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
public interface CourseMapper {

    @Mapping(target = "status", ignore = true)
    @Mapping(target = "level", ignore = true)
    Course toEntity(CourseCreateRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "level", ignore = true)
    void updateEntity(CourseUpdateRequest request, @MappingTarget Course course);
}
