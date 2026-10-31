package com.course_aura_hub.file.config;

import com.course_aura_hub.file.storage.DisabledObjectStore;
import com.course_aura_hub.file.storage.ObjectStore;
import com.course_aura_hub.file.storage.S3ObjectStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FileStorageConfig {

    @Bean
    public ObjectStore objectStore(StorageProperties properties) {
        return properties.enabled() ? new S3ObjectStore(properties) : new DisabledObjectStore();
    }
}
