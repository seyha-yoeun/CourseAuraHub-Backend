package com.course_aura_hub.certificate.service;

import com.course_aura_hub.certificate.config.CertificateProperties;
import com.course_aura_hub.certificate.model.CourseCertificate;
import com.course_aura_hub.common.exception.BusinessRuleException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CertificatePdfRendererTest {

    private static final String NUMBER = "CAH-7K2M-Q9X4-ZD3P-8HRT";

    private final CertificatePdfRenderer renderer =
            new CertificatePdfRenderer(new CertificateProperties("https://example.com/certificates/"), new CertificateTextOutliner());

    private CourseCertificate certificate(String learnerName, String courseTitle) {
        return CourseCertificate.builder()
                .certificateNumber(NUMBER)
                .learnerName(learnerName)
                .courseTitle(courseTitle)
                .instructor("Ada Lovelace")
                .issuedAt(Instant.parse("2026-03-09T12:00:00Z"))
                .build();
    }

    private String text(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            return new PDFTextStripper().getText(document);
        }
    }

    @Test
    void render_printsEveryRequiredDetail() throws Exception {
        assertThat(text(renderer.render(certificate("Jane Learner", "Practical Kubernetes"))))
                .contains(
                        "Jane Learner",
                        "Practical Kubernetes",
                        "Ada Lovelace",
                        "March 9, 2026",
                        NUMBER,
                        "https://example.com/certificates/" + NUMBER);
    }

    @Test
    void render_fallsBackToTheUnicodeFontForCyrillicNames() throws Exception {
        assertThat(text(renderer.render(certificate("Анна Иванова", "Practical Kubernetes")))).contains("Анна Иванова");
    }

    @Test
    void render_keepsVeryLongNamesAndTitlesOnOnePage() throws Exception {
        String longName = "Bartholomew Maximilian Featherstonehaugh-Cholmondeley ".repeat(3);
        String longTitle = "A Complete and Unusually Thorough Introduction to Distributed Systems ".repeat(8);

        assertThat(text(renderer.render(certificate(longName, longTitle)))).contains(NUMBER);
    }

    @Test
    void render_drawsKhmerNamesAndWording() throws Exception {
        CourseCertificate certificate = certificate("សុខ សុភា Sophea", "វគ្គសិក្សា Git");

        assertThat(text(renderer.render(certificate))).contains(NUMBER);
        try (PDDocument document = Loader.loadPDF(renderer.render(certificate, "km"))) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
        }
    }

    @Test
    void render_explainsWhenAScriptCannotBeDrawn() {
        assertThatThrownBy(() -> renderer.render(certificate("王小明", "Practical Kubernetes")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Print");
    }
}
