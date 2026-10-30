package com.course_aura_hub.certificate.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Generates unguessable certificate numbers such as {@code CAH-7K2M-Q9X4-ZD3P-8HRT}.
 * Numbers are public (they are what a learner shares), so they carry 80 random bits to resist enumeration.
 */
@Component
public class CertificateNumberGenerator {

    // Crockford base32: no I, L, O or U, so numbers survive being read aloud or retyped
    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final int GROUPS = 4;
    private static final int GROUP_LENGTH = 4;
    private static final Pattern FORMAT = Pattern.compile("CAH(-[0-9A-HJKMNP-TV-Z]{4}){4}");

    private final SecureRandom random = new SecureRandom();

    public String next() {
        StringBuilder number = new StringBuilder("CAH");
        for (int group = 0; group < GROUPS; group++) {
            number.append('-');
            for (int i = 0; i < GROUP_LENGTH; i++) {
                number.append(ALPHABET[random.nextInt(ALPHABET.length)]);
            }
        }
        return number.toString();
    }

    /**
     * Canonical form of user-supplied input (trimmed, upper-case), or {@code null} when it cannot be a certificate number.
     */
    public static String normalize(String input) {
        if (input == null) {
            return null;
        }
        String candidate = input.trim().toUpperCase(Locale.ROOT);
        return FORMAT.matcher(candidate).matches() ? candidate : null;
    }
}
