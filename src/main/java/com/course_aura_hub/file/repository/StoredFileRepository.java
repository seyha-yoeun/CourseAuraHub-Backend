package com.course_aura_hub.file.repository;

import com.course_aura_hub.file.model.FileStatus;
import com.course_aura_hub.file.model.StoredFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StoredFileRepository extends JpaRepository<StoredFile, UUID> {

    Optional<StoredFile> findByIdAndUploadedBy(UUID id, UUID uploadedBy);

    Optional<StoredFile> findByObjectKey(String objectKey);

    long countByUploadedByAndCreatedAtAfter(UUID uploadedBy, Instant after);

    List<StoredFile> findTop200ByStatusAndUpdatedAtBefore(FileStatus status, Instant before);
}
