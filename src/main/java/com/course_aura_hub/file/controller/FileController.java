package com.course_aura_hub.file.controller;

import com.course_aura_hub.common.dto.ApiResponse;
import com.course_aura_hub.common.security.SecurityUtils;
import com.course_aura_hub.file.dto.FileUploadRequest;
import com.course_aura_hub.file.dto.FileUploadResponse;
import com.course_aura_hub.file.dto.StoredFileResponse;
import com.course_aura_hub.file.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/me/files")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "Files", description = "Direct uploads to object storage")
public class FileController {

    private final FileService fileService;

    @PostMapping
    @Operation(
            summary = "Start an upload",
            description = "Returns a short-lived URL to PUT the file to. Purposes: AVATAR, plus COURSE_THUMBNAIL and "
                    + "LESSON_CONTENT for admins."
    )
    public ResponseEntity<ApiResponse<FileUploadResponse>> startUpload(
            @Valid @RequestBody FileUploadRequest request,
            Authentication authentication
    ) {
        FileUploadResponse response = fileService.startUpload(
                authentication.getName(), SecurityUtils.isAdmin(authentication), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PostMapping("/{id}/complete")
    @Operation(
            summary = "Finish an upload",
            description = "Checks the uploaded file and returns the URL to save on the course, profile or lesson."
    )
    public ResponseEntity<ApiResponse<StoredFileResponse>> completeUpload(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        return ResponseEntity.ok(ApiResponse.success(fileService.completeUpload(authentication.getName(), id)));
    }
}
