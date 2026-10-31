package com.course_aura_hub.file.dto;

import java.util.UUID;

/**
 * @param uploadUrl where the browser PUTs the file, sending exactly this content type
 */
public record FileUploadResponse(UUID id, String uploadUrl, String contentType) {
}
