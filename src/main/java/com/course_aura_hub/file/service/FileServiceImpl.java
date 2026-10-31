package com.course_aura_hub.file.service;

import com.course_aura_hub.auth.repository.UserRepository;
import com.course_aura_hub.common.exception.BusinessRuleException;
import com.course_aura_hub.common.exception.ForbiddenActionException;
import com.course_aura_hub.common.exception.ResourceNotFoundException;
import com.course_aura_hub.file.config.StorageProperties;
import com.course_aura_hub.file.dto.FileUploadRequest;
import com.course_aura_hub.file.dto.FileUploadResponse;
import com.course_aura_hub.file.dto.StoredFileResponse;
import com.course_aura_hub.file.model.FilePurpose;
import com.course_aura_hub.file.model.FileStatus;
import com.course_aura_hub.file.model.FileType;
import com.course_aura_hub.file.model.StoredFile;
import com.course_aura_hub.file.repository.StoredFileRepository;
import com.course_aura_hub.file.storage.ObjectStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Browsers upload straight to object storage, so large videos never pass through this server. Uploads land
 * in a private folder and only reach their final location after {@link #completeUpload} has checked them.
 * Storage calls happen outside transactions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private static final String REFERENCE_PREFIX = "storage://";
    private static final String UPLOAD_FOLDER = "uploads/";
    private static final String PRIVATE_FOLDER = "private/";
    private static final int MAX_UPLOADS_PER_HOUR = 20;
    private static final Duration CLEANUP_DELAY = Duration.ofDays(1);

    private final StoredFileRepository fileRepository;
    private final UserRepository userRepository;
    private final ObjectStore objectStore;
    private final StorageProperties properties;

    @Override
    public FileUploadResponse startUpload(String email, boolean admin, FileUploadRequest request) {
        FilePurpose purpose = FilePurpose.fromValue(request.purpose());
        if (purpose.adminOnly() && !admin) {
            throw new ForbiddenActionException("Only admins can upload this file");
        }
        FileType type = FileType.fromContentType(request.contentType())
                .filter(purpose.allowedTypes()::contains)
                .orElseThrow(() -> new BusinessRuleException("This file type isn't allowed here"));
        if (request.size() > purpose.maxBytes()) {
            throw new BusinessRuleException("This file is too large. The limit is %d MB."
                    .formatted(purpose.maxBytes() / (1024 * 1024)));
        }

        UUID userId = findUserId(email);
        Instant now = Instant.now();
        if (!admin && fileRepository.countByUploadedByAndCreatedAtAfter(userId, now.minus(Duration.ofHours(1)))
                >= MAX_UPLOADS_PER_HOUR) {
            throw new BusinessRuleException("Too many uploads. Please try again later.");
        }

        UUID id = UUID.randomUUID();
        String uploadUrl = objectStore.presignUpload(
                uploadKey(id), type.contentType(), request.size(), properties.uploadUrlTtl());
        fileRepository.save(StoredFile.builder()
                .id(id)
                .uploadedBy(userId)
                .purpose(purpose)
                .objectKey("%s/%s.%s".formatted(purpose.folder(), id, type.extension()))
                .contentType(type.contentType())
                .sizeBytes(request.size())
                .originalName(request.fileName().trim())
                .status(FileStatus.PENDING)
                .createdAt(now)
                .updatedAt(now)
                .build());
        return new FileUploadResponse(id, uploadUrl, type.contentType());
    }

    @Override
    public StoredFileResponse completeUpload(String email, UUID fileId) {
        StoredFile file = fileRepository.findByIdAndUploadedBy(fileId, findUserId(email))
                .filter(found -> found.getStatus() != FileStatus.RELEASED)
                .orElseThrow(() -> ResourceNotFoundException.of("File", fileId));
        if (file.getStatus() == FileStatus.READY) {
            return toResponse(file);
        }

        String uploadKey = uploadKey(fileId);
        long size = objectStore.size(uploadKey)
                .orElseThrow(() -> new BusinessRuleException("The file hasn't finished uploading"));
        FileType type = FileType.fromContentType(file.getContentType())
                .orElseThrow(() -> new BusinessRuleException("This file type isn't allowed here"));
        if (size != file.getSizeBytes() || !type.matches(objectStore.readStart(uploadKey, FileType.SIGNATURE_LENGTH))) {
            objectStore.delete(uploadKey);
            fileRepository.delete(file);
            throw new BusinessRuleException("The file's contents don't match its type or size");
        }

        objectStore.move(uploadKey, file.getObjectKey());
        file.setStatus(FileStatus.READY);
        file.setUpdatedAt(Instant.now());
        log.info("Stored file id={} purpose={} size={}", file.getId(), file.getPurpose(), size);
        return toResponse(fileRepository.save(file));
    }

    @Override
    public String playableUrl(String contentUrl) {
        if (contentUrl == null || !contentUrl.startsWith(REFERENCE_PREFIX)) {
            return contentUrl;
        }
        if (!properties.enabled()) {
            return null;
        }
        return objectStore.presignDownload(contentUrl.substring(REFERENCE_PREFIX.length()), properties.downloadUrlTtl());
    }

    @Override
    public String toStoredUrl(String contentUrl) {
        if (!StringUtils.hasText(contentUrl) || !properties.enabled()) {
            return contentUrl;
        }
        String bucketUrl = properties.browserEndpoint() + "/" + properties.bucket() + "/";
        if (!contentUrl.startsWith(bucketUrl + PRIVATE_FOLDER)) {
            return contentUrl;
        }
        String key = contentUrl.substring(bucketUrl.length());
        int query = key.indexOf('?');
        return REFERENCE_PREFIX + (query < 0 ? key : key.substring(0, query));
    }

    @Override
    public void release(String url, UUID userId) {
        String base = properties.publicBaseUrl() + "/";
        if (url == null || !url.startsWith(base)) {
            return;
        }
        fileRepository.findByObjectKey(url.substring(base.length()))
                .filter(file -> userId.equals(file.getUploadedBy()) && file.getStatus() == FileStatus.READY)
                .ifPresent(file -> {
                    file.setStatus(FileStatus.RELEASED);
                    file.setUpdatedAt(Instant.now());
                    fileRepository.save(file);
                });
    }

    @Override
    public void cleanUp() {
        Instant before = Instant.now().minus(CLEANUP_DELAY);
        List<StoredFile> unfinished = fileRepository.findTop200ByStatusAndUpdatedAtBefore(FileStatus.PENDING, before);
        List<StoredFile> released = fileRepository.findTop200ByStatusAndUpdatedAtBefore(FileStatus.RELEASED, before);
        unfinished.forEach(file -> remove(file, uploadKey(file.getId())));
        released.forEach(file -> remove(file, file.getObjectKey()));
    }

    private void remove(StoredFile file, String key) {
        objectStore.delete(key);
        fileRepository.delete(file);
    }

    private StoredFileResponse toResponse(StoredFile file) {
        String url = file.getPurpose().isPublic()
                ? properties.publicBaseUrl() + "/" + file.getObjectKey()
                : REFERENCE_PREFIX + file.getObjectKey();
        return new StoredFileResponse(file.getId(), url, file.getContentType(), file.getSizeBytes(), file.getOriginalName());
    }

    private String uploadKey(UUID id) {
        return UPLOAD_FOLDER + id;
    }

    private UUID findUserId(String email) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        return userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> ResourceNotFoundException.of("User", normalizedEmail))
                .getId();
    }
}
