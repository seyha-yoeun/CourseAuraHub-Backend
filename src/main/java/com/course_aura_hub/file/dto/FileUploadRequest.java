package com.course_aura_hub.file.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record FileUploadRequest(

        @NotBlank(message = "File purpose is required")
        String purpose,

        @NotBlank(message = "File name is required")
        @Size(max = 255, message = "File name must not exceed 255 characters")
        String fileName,

        @NotBlank(message = "Content type is required")
        @Size(max = 100, message = "Content type must not exceed 100 characters")
        String contentType,

        @NotNull(message = "File size is required")
        @Positive(message = "File cannot be empty")
        Long size
) {
}
