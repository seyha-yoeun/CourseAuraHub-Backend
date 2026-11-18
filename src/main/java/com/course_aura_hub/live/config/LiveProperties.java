package com.course_aura_hub.live.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param joinEarlyMinutes how long before the start learners can join
 * @param apiBaseUrl       public URL of this API (ending in /api/v1), for calendar feed links
 * @param siteUrl          public URL of the web app, linked from calendar events
 */
@ConfigurationProperties(prefix = "app.live")
public record LiveProperties(
        @DefaultValue("15") int joinEarlyMinutes,
        @DefaultValue("http://localhost:8080/api/v1") String apiBaseUrl,
        @DefaultValue("http://localhost:3000") String siteUrl
) {

    public LiveProperties {
        apiBaseUrl = trim(apiBaseUrl);
        siteUrl = trim(siteUrl);
    }

    private static String trim(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
