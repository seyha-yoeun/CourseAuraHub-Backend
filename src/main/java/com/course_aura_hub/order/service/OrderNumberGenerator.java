package com.course_aura_hub.order.service;

import java.security.SecureRandom;

/**
 * Unguessable order numbers: 80 random bits, so they can appear in provider URLs and receipts.
 */
final class OrderNumberGenerator {

    // Crockford base32: no I, L, O or U
    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final int LENGTH = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private OrderNumberGenerator() {
    }

    static String next() {
        StringBuilder number = new StringBuilder("ORD-");
        for (int i = 0; i < LENGTH; i++) {
            number.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return number.toString();
    }
}
