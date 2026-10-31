package com.course_aura_hub.file.service;

import com.course_aura_hub.file.dto.FileUploadRequest;
import com.course_aura_hub.file.dto.FileUploadResponse;
import com.course_aura_hub.file.dto.StoredFileResponse;

import java.util.UUID;

public interface FileService {

    FileUploadResponse startUpload(String email, boolean admin, FileUploadRequest request);

    /** Checks what the browser really uploaded and moves it to its final location. */
    StoredFileResponse completeUpload(String email, UUID fileId);

    /** Turns a storage:// reference into a signed link. Any other URL is returned unchanged. */
    String playableUrl(String contentUrl);

    /** The value to store for a lesson: a signed link sent back by the edit form becomes its reference again. */
    String toStoredUrl(String contentUrl);

    /** Marks a replaced file for cleanup, if it is one this user uploaded. Other URLs are ignored. */
    void release(String url, UUID userId);

    void cleanUp();
}
