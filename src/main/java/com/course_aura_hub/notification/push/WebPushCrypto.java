package com.course_aura_hub.notification.push;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;

/**
 * Message encryption for Web Push (RFC 8291, {@code aes128gcm} content coding from RFC 8188) with the JDK only.
 * Each message uses a fresh server key pair and salt, so no two messages share a key.
 */
public final class WebPushCrypto {

    private static final int RECORD_SIZE = 4096;
    private static final int KEY_LENGTH = 65;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ECParameterSpec P256 = p256();

    private WebPushCrypto() {
    }

    /**
     * Encrypts a payload for one browser.
     *
     * @param browserPublicKey the subscription's p256dh key, base64url
     * @param authSecret       the subscription's auth secret, base64url
     * @return the request body: header (salt, record size, server key) followed by the ciphertext
     */
    public static byte[] encrypt(byte[] payload, String browserPublicKey, String authSecret) {
        try {
            byte[] uaPublic = decode(browserPublicKey);
            byte[] auth = decode(authSecret);
            KeyPair server = generateKeyPair();
            byte[] asPublic = encodePublicKey((ECPublicKey) server.getPublic());
            byte[] salt = new byte[16];
            RANDOM.nextBytes(salt);

            KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
            agreement.init(server.getPrivate());
            agreement.doPhase(decodePublicKey(uaPublic), true);
            byte[] sharedSecret = agreement.generateSecret();

            byte[] keyInfo = concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), uaPublic, asPublic);
            byte[] ikm = hkdf(auth, sharedSecret, keyInfo, 32);
            byte[] prk = hmac(salt, ikm);
            byte[] contentKey = expand(prk, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
            byte[] nonce = expand(prk, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);

            // One record, so the padding delimiter is 0x02 (last record) with no padding after it
            byte[] plaintext = Arrays.copyOf(payload, payload.length + 1);
            plaintext[payload.length] = 2;
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(contentKey, "AES"), new GCMParameterSpec(128, nonce));
            byte[] ciphertext = cipher.doFinal(plaintext);

            ByteBuffer body = ByteBuffer.allocate(16 + 4 + 1 + KEY_LENGTH + ciphertext.length);
            body.put(salt).putInt(RECORD_SIZE).put((byte) KEY_LENGTH).put(asPublic).put(ciphertext);
            return body.array();
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("Could not encrypt the push message", ex);
        }
    }

    static KeyPair generateKeyPair() throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"), RANDOM);
        return generator.generateKeyPair();
    }

    /** The 65-byte uncompressed form browsers and VAPID use: 0x04, then X and Y. */
    public static byte[] encodePublicKey(ECPublicKey key) {
        byte[] out = new byte[KEY_LENGTH];
        out[0] = 4;
        copyCoordinate(key.getW().getAffineX(), out, 1);
        copyCoordinate(key.getW().getAffineY(), out, 33);
        return out;
    }

    public static ECPublicKey decodePublicKey(byte[] uncompressed) throws GeneralSecurityException {
        if (uncompressed.length != KEY_LENGTH || uncompressed[0] != 4) {
            throw new IllegalArgumentException("Not an uncompressed P-256 public key");
        }
        ECPoint point = new ECPoint(new BigInteger(1, Arrays.copyOfRange(uncompressed, 1, 33)),
                new BigInteger(1, Arrays.copyOfRange(uncompressed, 33, KEY_LENGTH)));
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, P256));
    }

    /** A VAPID private key, given as the base64url 32-byte scalar that key generators print. */
    public static ECPrivateKey decodePrivateKey(String base64Url) throws GeneralSecurityException {
        return (ECPrivateKey) KeyFactory.getInstance("EC")
                .generatePrivate(new ECPrivateKeySpec(new BigInteger(1, decode(base64Url)), P256));
    }

    public static byte[] decode(String base64Url) {
        return Base64.getUrlDecoder().decode(base64Url.trim().replace('+', '-').replace('/', '_').replace("=", ""));
    }

    public static String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static byte[] hkdf(byte[] salt, byte[] ikm, byte[] info, int length) throws GeneralSecurityException {
        return expand(hmac(salt, ikm), info, length);
    }

    /** HKDF-Expand for at most one hash block, which is all Web Push needs. */
    static byte[] expand(byte[] prk, byte[] info, int length) throws GeneralSecurityException {
        return Arrays.copyOf(hmac(prk, concat(info, new byte[]{1})), length);
    }

    static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    static byte[] concat(byte[]... parts) {
        int length = 0;
        for (byte[] part : parts) {
            length += part.length;
        }
        ByteBuffer buffer = ByteBuffer.allocate(length);
        for (byte[] part : parts) {
            buffer.put(part);
        }
        return buffer.array();
    }

    private static void copyCoordinate(BigInteger value, byte[] out, int offset) {
        byte[] bytes = value.toByteArray();
        // toByteArray may add a sign byte or drop leading zeros; P-256 coordinates are always 32 bytes
        int start = Math.max(0, bytes.length - 32);
        int length = bytes.length - start;
        System.arraycopy(bytes, start, out, offset + 32 - length, length);
    }

    private static ECParameterSpec p256() {
        try {
            AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
            parameters.init(new ECGenParameterSpec("secp256r1"));
            return parameters.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }
}
