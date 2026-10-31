package com.course_aura_hub.file;

import com.course_aura_hub.file.model.FileType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class FileTypeTest {

    private byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.ISO_8859_1);
    }

    @Test
    void matches_recognisesRealFileStarts() {
        assertThat(FileType.JPEG.matches(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0})).isTrue();
        assertThat(FileType.PNG.matches(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0})).isTrue();
        assertThat(FileType.WEBP.matches(ascii("RIFF\0\0\0\0WEBP"))).isTrue();
        assertThat(FileType.MP4.matches(ascii("\0\0\0 ftypisom"))).isTrue();
        assertThat(FileType.WEBM.matches(new byte[]{0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, 0})).isTrue();
        assertThat(FileType.PDF.matches(ascii("%PDF-1.7"))).isTrue();
    }

    @Test
    void matches_rejectsOtherContent() {
        assertThat(FileType.PNG.matches(ascii("<svg onload=alert(1)>"))).isFalse();
        assertThat(FileType.MP4.matches(ascii("abc"))).isFalse();
        assertThat(FileType.WEBP.matches(ascii("RIFF\0\0\0\0WAVE"))).isFalse();
    }

    @Test
    void fromContentType_ignoresCaseAndRejectsUnknownTypes() {
        assertThat(FileType.fromContentType("IMAGE/PNG")).contains(FileType.PNG);
        assertThat(FileType.fromContentType("image/svg+xml")).isEmpty();
    }
}
