package com.course_aura_hub.file.dto;

import java.util.UUID;

/**
 * @param url the value to save on the course, profile or lesson. Private files use a storage:// reference.
 */
public record StoredFileResponse(UUID id, String url, String contentType, long size, String fileName) {
}
