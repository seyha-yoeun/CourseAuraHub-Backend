package com.course_aura_hub.auth.repository;

import com.course_aura_hub.auth.model.AuthProvider;
import com.course_aura_hub.auth.model.Role;
import com.course_aura_hub.auth.model.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByProviderAndProviderId(AuthProvider provider, String providerId);

    @Query("select u.id from User u where u.role = :role and u.enabled = true")
    List<UUID> findEnabledIdsByRole(@Param("role") Role role);

    /**
     * Row-locks the user for the rest of the transaction. Every flow that mutates credentials,
     * one-time tokens or sessions for a user locks the user row first, which serializes concurrent
     * requests (double OTP submission, parallel resets) and keeps lock ordering consistent.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.email = :email")
    Optional<User> findByEmailForUpdate(@Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") UUID id);
}
