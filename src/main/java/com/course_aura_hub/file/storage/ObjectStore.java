package com.course_aura_hub.file.storage;

import java.time.Duration;
import java.util.Optional;

/** S3-compatible object storage. Local development uses MinIO. */
public interface ObjectStore {

    /** A URL a browser can PUT this exact file to. The content type and size are part of the signature. */
    String presignUpload(String key, String contentType, long size, Duration ttl);

    String presignDownload(String key, Duration ttl);

    /** The object's size, or empty if it doesn't exist. */
    Optional<Long> size(String key);

    byte[] readStart(String key, int length);

    void move(String fromKey, String toKey);

    void delete(String key);
}
