package com.course_aura_hub.notification.push;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WebPushCryptoTest {

    @Test
    void aBrowserCanDecryptTheMessage() throws Exception {
        KeyPair browser = WebPushCrypto.generateKeyPair();
        byte[] browserPublic = WebPushCrypto.encodePublicKey((ECPublicKey) browser.getPublic());
        byte[] auth = new byte[16];
        new java.security.SecureRandom().nextBytes(auth);
        byte[] payload = "{\"title\":\"Hello\"}".getBytes(StandardCharsets.UTF_8);

        byte[] body = WebPushCrypto.encrypt(payload, WebPushCrypto.encode(browserPublic), WebPushCrypto.encode(auth));

        assertThat(decrypt(body, browser, browserPublic, auth)).isEqualTo(payload);
    }

    @Test
    void eachMessageUsesAFreshKeyAndSalt() throws Exception {
        KeyPair browser = WebPushCrypto.generateKeyPair();
        String browserPublic = WebPushCrypto.encode(WebPushCrypto.encodePublicKey((ECPublicKey) browser.getPublic()));
        String auth = WebPushCrypto.encode(new byte[16]);

        byte[] first = WebPushCrypto.encrypt(new byte[]{1}, browserPublic, auth);
        byte[] second = WebPushCrypto.encrypt(new byte[]{1}, browserPublic, auth);

        assertThat(Arrays.copyOf(first, 102)).isNotEqualTo(Arrays.copyOf(second, 102));
    }

    @Test
    void theVapidHeaderIsSignedForThePushServicesOrigin() throws Exception {
        KeyPair vapid = WebPushCrypto.generateKeyPair();
        String publicKey = WebPushCrypto.encode(WebPushCrypto.encodePublicKey((ECPublicKey) vapid.getPublic()));
        String privateKey = WebPushCrypto.encode(scalar((ECPrivateKey) vapid.getPrivate()));
        WebPushClient client = new WebPushClient(new WebPushProperties(publicKey, privateKey, "mailto:ops@example.com",
                60, List.of("push.example.com")));

        String header = client.authorization(URI.create("https://push.example.com/send/abc"));

        assertThat(header).startsWith("vapid t=").endsWith(", k=" + publicKey);
        String token = header.substring("vapid t=".length(), header.indexOf(','));
        Claims claims = Jwts.parser().verifyWith(vapid.getPublic()).build().parseSignedClaims(token).getPayload();
        assertThat(claims.getAudience()).containsExactly("https://push.example.com");
        assertThat(claims.getSubject()).isEqualTo("mailto:ops@example.com");
    }

    @Test
    void onlyAllowedPushServicesAreAccepted() {
        WebPushProperties properties = new WebPushProperties("k", "k", "mailto:a@b.c", 60, List.of("fcm.googleapis.com"));

        assertThat(properties.allowsHost("fcm.googleapis.com")).isTrue();
        assertThat(properties.allowsHost("eu.fcm.googleapis.com")).isTrue();
        assertThat(properties.allowsHost("evilfcm.googleapis.com.example.com")).isFalse();
        assertThat(properties.allowsHost("localhost")).isFalse();
    }

    /** RFC 8291 decryption, as a browser does it. */
    private static byte[] decrypt(byte[] body, KeyPair browser, byte[] browserPublic, byte[] auth) throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(body);
        byte[] salt = new byte[16];
        buffer.get(salt);
        assertThat(buffer.getInt()).isEqualTo(4096);
        byte[] serverPublic = new byte[buffer.get()];
        buffer.get(serverPublic);
        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);

        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(browser.getPrivate());
        agreement.doPhase(WebPushCrypto.decodePublicKey(serverPublic), true);
        byte[] keyInfo = WebPushCrypto.concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), browserPublic, serverPublic);
        byte[] ikm = WebPushCrypto.hkdf(auth, agreement.generateSecret(), keyInfo, 32);
        byte[] prk = WebPushCrypto.hmac(salt, ikm);
        byte[] key = WebPushCrypto.expand(prk, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
        byte[] nonce = WebPushCrypto.expand(prk, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        byte[] plaintext = cipher.doFinal(ciphertext);
        assertThat(plaintext[plaintext.length - 1]).isEqualTo((byte) 2);
        return Arrays.copyOf(plaintext, plaintext.length - 1);
    }

    private static byte[] scalar(ECPrivateKey key) {
        byte[] bytes = key.getS().toByteArray();
        byte[] out = new byte[32];
        int start = Math.max(0, bytes.length - 32);
        System.arraycopy(bytes, start, out, 32 - (bytes.length - start), bytes.length - start);
        return out;
    }
}
