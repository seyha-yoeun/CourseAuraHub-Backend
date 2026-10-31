package com.course_aura_hub.file.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stored_files")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoredFile {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "uploaded_by", updatable = false)
    private UUID uploadedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, updatable = false, columnDefinition = "text")
    private FilePurpose purpose;

    @Column(name = "object_key", nullable = false, updatable = false, columnDefinition = "text")
    private String objectKey;

    @Column(name = "content_type", nullable = false, updatable = false, columnDefinition = "text")
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Column(name = "original_name", nullable = false, updatable = false, columnDefinition = "text")
    private String originalName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "text")
    private FileStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
