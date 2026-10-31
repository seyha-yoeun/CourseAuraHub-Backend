package com.course_aura_hub.file.storage;

import com.course_aura_hub.common.exception.ServiceUnavailableException;

import java.time.Duration;
import java.util.Optional;

public class DisabledObjectStore implements ObjectStore {

    private static ServiceUnavailableException disabled() {
        return new ServiceUnavailableException("File uploads are not configured");
    }

    @Override
    public String presignUpload(String key, String contentType, long size, Duration ttl) {
        throw disabled();
    }

    @Override
    public String presignDownload(String key, Duration ttl) {
        throw disabled();
    }

    @Override
    public Optional<Long> size(String key) {
        throw disabled();
    }

    @Override
    public byte[] readStart(String key, int length) {
        throw disabled();
    }

    @Override
    public void move(String fromKey, String toKey) {
        throw disabled();
    }

    @Override
    public void delete(String key) {
        throw disabled();
    }
}
