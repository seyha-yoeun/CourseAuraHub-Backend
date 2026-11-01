package com.course_aura_hub.membercard.service;

import java.security.SecureRandom;

/**
 * Card numbers grouped for reading, such as MC-7K2M-Q9X4-ZD3P. A number alone can't spend a balance.
 */
final class MemberCardNumberGenerator {

    // Crockford base32: no I, L, O or U
    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final int GROUPS = 3;
    private static final int GROUP_LENGTH = 4;
    private static final SecureRandom RANDOM = new SecureRandom();

    private MemberCardNumberGenerator() {
    }

    static String next() {
        StringBuilder number = new StringBuilder("MC");
        for (int group = 0; group < GROUPS; group++) {
            number.append('-');
            for (int i = 0; i < GROUP_LENGTH; i++) {
                number.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
            }
        }
        return number.toString();
    }
}
