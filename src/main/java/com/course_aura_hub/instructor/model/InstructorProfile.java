package com.course_aura_hub.instructor.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** The public face of an instructor, found by its handle. Shown only while the user can teach. */
@Entity
@Table(name = "instructor_profiles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstructorProfile {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "handle", nullable = false, unique = true, columnDefinition = "text")
    private String handle;

    @Column(name = "headline", columnDefinition = "text")
    private String headline;

    @Column(name = "linkedin_url", columnDefinition = "text")
    private String linkedinUrl;

    @Column(name = "github_url", columnDefinition = "text")
    private String githubUrl;

    @Column(name = "youtube_url", columnDefinition = "text")
    private String youtubeUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
