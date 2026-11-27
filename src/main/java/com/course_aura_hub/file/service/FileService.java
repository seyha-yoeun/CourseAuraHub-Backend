package com.course_aura_hub.file.service;

import com.course_aura_hub.file.dto.FileUploadRequest;
import com.course_aura_hub.file.dto.FileUploadResponse;
import com.course_aura_hub.file.dto.StoredFileResponse;

import com.course_aura_hub.file.model.FilePurpose;

import java.util.UUID;

public interface FileService {

    FileUploadResponse startUpload(String email, boolean admin, FileUploadRequest request);

    /** Checks what the browser really uploaded and moves it to its final location. */
    StoredFileResponse completeUpload(String email, UUID fileId);

    /** Turns a storage:// reference into a signed link. Any other URL is returned unchanged. */
    String playableUrl(String contentUrl);

    /** The value to store for a lesson: a signed link sent back by the edit form becomes its reference again. */
    String toStoredUrl(String contentUrl);

    /**
     * Refuses a storage:// reference unless this user uploaded it for this purpose. Signed links are handed
     * out for stored references, so without this anyone could point a record at another person's private file.
     */
    void requireOwned(String reference, String email, FilePurpose purpose);

    /**
     * A finished upload this user made for this purpose, looked up by its id; the response's url is the
     * storage:// reference for private purposes. Anything else is refused the same way as {@link #requireOwned}.
     */
    StoredFileResponse requireOwnedFile(UUID fileId, UUID userId, FilePurpose purpose);

    /** Marks a replaced or deleted file (public URL or storage:// reference) for cleanup, if this user uploaded it. Other URLs are ignored. */
    void release(String url, UUID userId);

    void cleanUp();
}
