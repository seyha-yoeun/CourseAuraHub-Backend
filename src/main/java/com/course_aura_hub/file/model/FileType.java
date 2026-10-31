package com.course_aura_hub.file.model;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** The file types we accept, recognised by their first bytes because a browser's content type can't be trusted. */
public enum FileType {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp"),
    MP4("video/mp4", "mp4"),
    WEBM("video/webm", "webm"),
    PDF("application/pdf", "pdf");

    /** How many leading bytes {@link #matches} needs. */
    public static final int SIGNATURE_LENGTH = 12;

    private static final byte[] JPEG_START = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_START = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] WEBM_START = {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3};

    private final String contentType;
    private final String extension;

    FileType(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public static Optional<FileType> fromContentType(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(type -> type.contentType.equals(normalized)).findFirst();
    }

    public boolean matches(byte[] start) {
        return switch (this) {
            case JPEG -> startsWith(start, 0, JPEG_START);
            case PNG -> startsWith(start, 0, PNG_START);
            case WEBP -> startsWith(start, 0, ascii("RIFF")) && startsWith(start, 8, ascii("WEBP"));
            case MP4 -> startsWith(start, 4, ascii("ftyp"));
            case WEBM -> startsWith(start, 0, WEBM_START);
            case PDF -> startsWith(start, 0, ascii("%PDF-"));
        };
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return extension;
    }

    private static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }

    private static boolean startsWith(byte[] data, int offset, byte[] expected) {
        return data.length >= offset + expected.length
                && Arrays.equals(data, offset, offset + expected.length, expected, 0, expected.length);
    }
}
