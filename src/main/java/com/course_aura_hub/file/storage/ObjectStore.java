package com.course_aura_hub.file.storage;

import java.nio.file.Path;
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

    /** The whole object, refused when it is larger than {@code maxBytes}. For small text such as playlists. */
    byte[] read(String key, int maxBytes);

    /** Saves the object to a local file, for server-side processing such as transcoding. */
    void download(String key, Path target);

    /** Stores a local file under this key. */
    void upload(String key, Path source, String contentType);

    void delete(String key);
}
