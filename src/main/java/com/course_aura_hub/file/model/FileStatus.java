package com.course_aura_hub.file.model;

public enum FileStatus {
    /** The browser was given an upload URL; the file is not checked yet. */
    PENDING,
    /** Checked and in its final location. */
    READY,
    /** No longer used. The cleanup job removes it. */
    RELEASED
}
