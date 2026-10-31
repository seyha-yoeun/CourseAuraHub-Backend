package com.course_aura_hub.file.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * @param endpoint       S3 API address the backend calls; blank turns uploads off
 * @param publicEndpoint S3 API address browsers can reach; defaults to {@code endpoint}
 * @param publicUrl      base URL of the bucket for public files, such as a CDN; defaults to the public endpoint
 * @param uploadUrlTtl   how long a browser has to start an upload
 * @param downloadUrlTtl how long a private lesson link works
 */
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        String endpoint,
        String publicEndpoint,
        String publicUrl,
        @DefaultValue("us-east-1") String region,
        @DefaultValue("course-aura") String bucket,
        String accessKey,
        String secretKey,
        @DefaultValue("PT15M") Duration uploadUrlTtl,
        @DefaultValue("PT6H") Duration downloadUrlTtl
) {

    public boolean enabled() {
        return StringUtils.hasText(endpoint);
    }

    public String browserEndpoint() {
        return trimSlash(StringUtils.hasText(publicEndpoint) ? publicEndpoint : endpoint);
    }

    public String publicBaseUrl() {
        return StringUtils.hasText(publicUrl) ? trimSlash(publicUrl) : browserEndpoint() + "/" + bucket;
    }

    private static String trimSlash(String url) {
        return url.replaceAll("/+$", "");
    }
}
