package com.course_aura_hub.student.repository;

import com.course_aura_hub.student.model.Student;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StudentRepository extends JpaRepository<Student, Integer>, JpaSpecificationExecutor<Student> {

    Optional<Student> findByEmailIgnoreCase(String email);

    Optional<Student> findByUserId(UUID userId);

    /** The accounts behind these students. Students without an account are left out. */
    @Query("select s.user.id from Student s where s.id in :ids and s.user is not null")
    List<UUID> findUserIdsByIdIn(@Param("ids") Collection<Integer> ids);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Integer id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Student s where s.id = :id")
    Optional<Student> findByIdForUpdate(@Param("id") Integer id);
}
