package com.course_aura_hub.student.service;

import com.course_aura_hub.student.dto.StudentCreateRequest;
import com.course_aura_hub.student.dto.StudentResponse;
import com.course_aura_hub.student.dto.StudentUpdateRequest;

import java.util.List;

public interface StudentService {

    List<StudentResponse> findAll(String status, String search);

    StudentResponse findById(Integer id);

    StudentResponse create(StudentCreateRequest request);

    StudentResponse update(Integer id, StudentUpdateRequest request);

    void delete(Integer id);
}
