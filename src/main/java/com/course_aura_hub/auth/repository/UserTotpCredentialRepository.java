package com.course_aura_hub.auth.repository;

import com.course_aura_hub.auth.model.UserTotpCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface UserTotpCredentialRepository extends JpaRepository<UserTotpCredential, UUID> {
}
