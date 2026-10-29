package com.course_aura_hub.auth.repository;

import com.course_aura_hub.auth.model.AuthProvider;
import com.course_aura_hub.auth.model.OAuthIdentity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OAuthIdentityRepository extends JpaRepository<OAuthIdentity, UUID> {

    @Query("""
            select i from OAuthIdentity i join fetch i.user
            where i.provider = :provider and i.providerUserId = :providerUserId
            """)
    Optional<OAuthIdentity> findByProviderAndProviderUserId(
            @Param("provider") AuthProvider provider,
            @Param("providerUserId") String providerUserId
    );

    boolean existsByUserIdAndProvider(UUID userId, AuthProvider provider);
}
