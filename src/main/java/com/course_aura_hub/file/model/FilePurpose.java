package com.course_aura_hub.file.model;

import com.course_aura_hub.common.exception.BusinessRuleException;

import java.util.Locale;
import java.util.Set;

/** What a file is for, which decides who may upload it, what it may be, and whether it is public. */
public enum FilePurpose {
    AVATAR("public/avatars", 5 * FilePurpose.MB, false, Set.of(FileType.JPEG, FileType.PNG, FileType.WEBP)),
    COURSE_THUMBNAIL("public/courses", 5 * FilePurpose.MB, true, Set.of(FileType.JPEG, FileType.PNG, FileType.WEBP)),
    // Lesson files stay private: paid content is only reachable through a short-lived signed link
    LESSON_CONTENT("private/lessons", 2048 * FilePurpose.MB, true, Set.of(FileType.MP4, FileType.WEBM, FileType.PDF));

    private static final long MB = 1024L * 1024L;

    private final String folder;
    private final long maxBytes;
    private final boolean adminOnly;
    private final Set<FileType> allowedTypes;

    FilePurpose(String folder, long maxBytes, boolean adminOnly, Set<FileType> allowedTypes) {
        this.folder = folder;
        this.maxBytes = maxBytes;
        this.adminOnly = adminOnly;
        this.allowedTypes = allowedTypes;
    }

    public static FilePurpose fromValue(String value) {
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("Unsupported file purpose: " + value);
        }
    }

    public boolean isPublic() {
        return folder.startsWith("public/");
    }

    public String folder() {
        return folder;
    }

    public long maxBytes() {
        return maxBytes;
    }

    public boolean adminOnly() {
        return adminOnly;
    }

    public Set<FileType> allowedTypes() {
        return allowedTypes;
    }
}
