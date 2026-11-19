package com.course_aura_hub.certificate.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Sharing certificates outside the app: Open Badges 2.0 documents and LinkedIn's add-to-profile link.
 *
 * @param apiBaseUrl             public URL of this API, ending in /api/v1; badge documents link to each other with it
 * @param siteUrl                public URL of the web app, used as the issuer's homepage
 * @param badgeImageUrl          badge image for courses without a thumbnail
 * @param issuerEmail            contact address published in the issuer profile; blank to leave it out
 * @param linkedinOrganizationId LinkedIn company id, so LinkedIn shows the platform's logo; blank uses the name
 */
@ConfigurationProperties(prefix = "app.certificates.sharing")
public record CertificateSharingProperties(
        @DefaultValue("http://localhost:8080/api/v1") String apiBaseUrl,
        @DefaultValue("http://localhost:3000") String siteUrl,
        @DefaultValue("http://localhost:3000/badge.png") String badgeImageUrl,
        @DefaultValue("") String issuerEmail,
        @DefaultValue("") String linkedinOrganizationId
) {

    public CertificateSharingProperties {
        apiBaseUrl = trimSlash(apiBaseUrl);
        siteUrl = trimSlash(siteUrl);
    }

    private static String trimSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
