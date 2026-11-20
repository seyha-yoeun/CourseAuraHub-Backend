package com.course_aura_hub.notification.push;

import com.course_aura_hub.learner.LearnerIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WebPushIntegrationTest extends LearnerIntegrationTestSupport {

    private static final String VAPID_PUBLIC;
    private static final String VAPID_PRIVATE;
    private static final String BROWSER_KEY;
    private static final String AUTH = WebPushCrypto.encode(new byte[16]);

    static {
        try {
            KeyPair vapid = WebPushCrypto.generateKeyPair();
            VAPID_PUBLIC = WebPushCrypto.encode(WebPushCrypto.encodePublicKey((ECPublicKey) vapid.getPublic()));
            byte[] scalar = ((ECPrivateKey) vapid.getPrivate()).getS().toByteArray();
            byte[] d = new byte[32];
            int start = Math.max(0, scalar.length - 32);
            System.arraycopy(scalar, start, d, 32 - (scalar.length - start), scalar.length - start);
            VAPID_PRIVATE = WebPushCrypto.encode(d);
            BROWSER_KEY = WebPushCrypto.encode(WebPushCrypto.encodePublicKey(
                    (ECPublicKey) WebPushCrypto.generateKeyPair().getPublic()));
        } catch (Exception ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    @DynamicPropertySource
    static void pushKeys(DynamicPropertyRegistry registry) {
        registry.add("app.push.vapid-public-key", () -> VAPID_PUBLIC);
        registry.add("app.push.vapid-private-key", () -> VAPID_PRIVATE);
    }

    @Test
    void aBrowserCanSubscribe_andUnsubscribe() throws Exception {
        TestUser user = registerUser();
        String endpoint = "https://fcm.googleapis.com/fcm/send/" + System.nanoTime();

        mockMvc.perform(as(user, get("/api/v1/push/vapid-public-key")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.publicKey").value(VAPID_PUBLIC));
        mockMvc.perform(asJson(user, post("/api/v1/me/push-subscriptions"), subscription(endpoint, BROWSER_KEY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subscribed").value(true))
                .andExpect(jsonPath("$.data.devices").value(1));
        // Subscribing the same browser again updates it instead of adding a device
        mockMvc.perform(asJson(user, post("/api/v1/me/push-subscriptions"), subscription(endpoint, BROWSER_KEY)))
                .andExpect(jsonPath("$.data.devices").value(1));

        mockMvc.perform(asJson(user, delete("/api/v1/me/push-subscriptions"), Map.of("endpoint", endpoint)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subscribed").value(false))
                .andExpect(jsonPath("$.data.devices").value(0));
    }

    @Test
    void onlyPushServicesAndValidKeysAreAccepted() throws Exception {
        TestUser user = registerUser();

        for (String endpoint : List.of("http://fcm.googleapis.com/fcm/send/x", "https://localhost:8080/x",
                "https://169.254.169.254/latest/meta-data", "https://evil.example.com/fcm.googleapis.com")) {
            mockMvc.perform(asJson(user, post("/api/v1/me/push-subscriptions"), subscription(endpoint, BROWSER_KEY)))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(asJson(user, post("/api/v1/me/push-subscriptions"),
                        subscription("https://fcm.googleapis.com/fcm/send/bad-key", WebPushCrypto.encode(new byte[65]))))
                .andExpect(status().isBadRequest());
        // With a CSRF token, so the request reaches authentication instead of being refused by the CSRF filter
        mockMvc.perform(post("/api/v1/me/push-subscriptions").with(csrf())).andExpect(status().isUnauthorized());
    }

    @Test
    void typesCanBeMuted() throws Exception {
        TestUser user = registerUser();

        mockMvc.perform(as(user, get("/api/v1/me/push-preferences")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.type == 'announcement')].enabled").value(true));
        mockMvc.perform(asJson(user, put("/api/v1/me/push-preferences"),
                        Map.of("preferences", List.of(Map.of("type", "announcement", "enabled", false)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.type == 'announcement')].enabled").value(false))
                .andExpect(jsonPath("$.data[?(@.type == 'enrolled')].enabled").value(true));
        mockMvc.perform(asJson(user, put("/api/v1/me/push-preferences"),
                        Map.of("preferences", List.of(Map.of("type", "nonsense", "enabled", false)))))
                .andExpect(status().isBadRequest());
    }

    private static Map<String, Object> subscription(String endpoint, String p256dh) {
        return Map.of("endpoint", endpoint, "keys", Map.of("p256dh", p256dh, "auth", AUTH));
    }
}
