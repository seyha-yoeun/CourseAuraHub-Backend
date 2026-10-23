package com.course_aura_hub.student.repository;

import com.course_aura_hub.student.model.Student;
import com.course_aura_hub.student.model.StudentCourse;
import com.course_aura_hub.student.model.StudentCourseId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentCourseRepository extends JpaRepository<StudentCourse, StudentCourseId> {

    @Query("""
            select sc
            from StudentCourse sc
            join fetch sc.course
            where sc.student.id = :studentId
            """)
    List<StudentCourse> findByStudentIdWithCourse(@Param("studentId") Integer studentId);

    @Query("""
            select sc.student
            from StudentCourse sc
            where sc.course.id = :courseId
            order by sc.student.joinedAt desc
            """)
    List<Student> findStudentsByCourseId(@Param("courseId") Integer courseId);

    Optional<StudentCourse> findByStudentIdAndCourseId(Integer studentId, Integer courseId);

    boolean existsByStudentIdAndCourseId(Integer studentId, Integer courseId);
}
