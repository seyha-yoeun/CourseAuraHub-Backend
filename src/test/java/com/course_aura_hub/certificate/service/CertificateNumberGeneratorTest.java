package com.course_aura_hub.certificate.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CertificateNumberGeneratorTest {

    private final CertificateNumberGenerator generator = new CertificateNumberGenerator();

    @Test
    void next_producesCanonicalUniqueNumbers() {
        Set<String> numbers = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            String number = generator.next();
            assertThat(number).matches("CAH(-[0-9A-HJKMNP-TV-Z]{4}){4}");
            assertThat(CertificateNumberGenerator.normalize(number)).isEqualTo(number);
            numbers.add(number);
        }
        assertThat(numbers).hasSize(1_000);
    }

    @Test
    void normalize_acceptsLowerCaseAndSurroundingSpace() {
        assertThat(CertificateNumberGenerator.normalize("  cah-7k2m-q9x4-zd3p-8hrt "))
                .isEqualTo("CAH-7K2M-Q9X4-ZD3P-8HRT");
    }

    @Test
    void normalize_rejectsMalformedInput() {
        assertThat(CertificateNumberGenerator.normalize(null)).isNull();
        assertThat(CertificateNumberGenerator.normalize("CAH-1234")).isNull();
        // I, L, O and U are not part of the alphabet
        assertThat(CertificateNumberGenerator.normalize("CAH-ILOU-0000-0000-0000")).isNull();
        assertThat(CertificateNumberGenerator.normalize("CAH-0000-0000-0000-0000' or 1=1")).isNull();
    }
}
