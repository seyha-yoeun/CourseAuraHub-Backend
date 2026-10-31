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

    @Query("select sc.student.id from StudentCourse sc where sc.course.id = :courseId")
    List<Integer> findStudentIdsByCourseId(@Param("courseId") Integer courseId);

    Optional<StudentCourse> findByStudentIdAndCourseId(Integer studentId, Integer courseId);

    boolean existsByStudentIdAndCourseId(Integer studentId, Integer courseId);

    @Query("""
            select count(sc) > 0
            from StudentCourse sc
            join sc.student s
            left join s.user u
            where sc.course.id = :courseId
              and (lower(u.email) = lower(:email) or (s.user is null and lower(s.email) = lower(:email)))
            """)
    boolean isUserEnrolled(@Param("email") String email, @Param("courseId") Integer courseId);
}
