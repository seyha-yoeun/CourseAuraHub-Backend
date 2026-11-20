package com.course_aura_hub.notification.push;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

/**
 * Browser push with VAPID. Generate a key pair once (for example {@code npx web-push generate-vapid-keys}) and keep
 * it: changing it invalidates every subscription.
 *
 * @param vapidPublicKey  base64url uncompressed P-256 public key, shared with browsers
 * @param vapidPrivateKey base64url P-256 private key; secret
 * @param subject         contact for push services, a mailto: or https: URL
 * @param ttlSeconds      how long a push service keeps a message for an offline browser
 * @param allowedHosts    push service host suffixes the server may post to; anything else is refused
 */
@ConfigurationProperties(prefix = "app.push")
public record WebPushProperties(
        @DefaultValue("") String vapidPublicKey,
        @DefaultValue("") String vapidPrivateKey,
        @DefaultValue("mailto:admin@courseaurahub.local") String subject,
        @DefaultValue("86400") int ttlSeconds,
        @DefaultValue({"fcm.googleapis.com", "updates.push.services.mozilla.com", "push.apple.com",
                "notify.windows.com"}) List<String> allowedHosts
) {

    public boolean isConfigured() {
        return StringUtils.hasText(vapidPublicKey) && StringUtils.hasText(vapidPrivateKey);
    }

    /** Whether the host is one of the allowed push services or a subdomain of one. */
    public boolean allowsHost(String host) {
        if (host == null) {
            return false;
        }
        String candidate = host.toLowerCase(Locale.ROOT);
        return allowedHosts.stream()
                .map(allowed -> allowed.trim().toLowerCase(Locale.ROOT))
                .anyMatch(allowed -> candidate.equals(allowed) || candidate.endsWith("." + allowed));
    }
}
