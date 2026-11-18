package com.course_aura_hub.notification.push;

import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.GeneralSecurityException;
import java.security.interfaces.ECPrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/** Posts encrypted messages to push services, signed with the VAPID key. */
@Slf4j
@Component
public class WebPushClient {

    private static final Duration TOKEN_LIFETIME = Duration.ofHours(12);

    private final WebPushProperties properties;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private volatile ECPrivateKey privateKey;

    public WebPushClient(WebPushProperties properties) {
        this.properties = properties;
    }

    /** @return the push service's HTTP status; 404 and 410 mean the subscription is gone */
    public int send(PushSubscription subscription, byte[] payload) throws IOException, InterruptedException {
        URI endpoint = URI.create(subscription.getEndpoint());
        byte[] body = WebPushCrypto.encrypt(payload, subscription.getP256dh(), subscription.getAuth());
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(10))
                .header("TTL", String.valueOf(properties.ttlSeconds()))
                .header("Content-Encoding", "aes128gcm")
                .header("Content-Type", "application/octet-stream")
                .header("Urgency", "normal")
                .header("Authorization", authorization(endpoint))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    /** The VAPID header (RFC 8292): a short-lived ES256 token for this push service's origin, and our public key. */
    String authorization(URI endpoint) {
        String audience = endpoint.getScheme() + "://" + endpoint.getHost()
                + (endpoint.getPort() == -1 ? "" : ":" + endpoint.getPort());
        String token = Jwts.builder()
                .header().add("typ", "JWT").and()
                .audience().single(audience)
                .subject(properties.subject())
                .expiration(Date.from(Instant.now().plus(TOKEN_LIFETIME)))
                .signWith(privateKey(), Jwts.SIG.ES256)
                .compact();
        return "vapid t=" + token + ", k=" + properties.vapidPublicKey().trim();
    }

    private ECPrivateKey privateKey() {
        ECPrivateKey key = privateKey;
        if (key == null) {
            try {
                key = WebPushCrypto.decodePrivateKey(properties.vapidPrivateKey());
            } catch (GeneralSecurityException | IllegalArgumentException ex) {
                throw new IllegalStateException("app.push.vapid-private-key is not a valid P-256 key", ex);
            }
            privateKey = key;
        }
        return key;
    }
}
