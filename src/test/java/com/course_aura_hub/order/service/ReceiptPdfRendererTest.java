package com.course_aura_hub.order.service;

import com.course_aura_hub.certificate.service.CertificateTextOutliner;
import com.course_aura_hub.course.model.Course;
import com.course_aura_hub.order.model.Order;
import com.course_aura_hub.order.model.OrderStatus;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptPdfRendererTest {

    private static final String NUMBER = "ORD-TEST-0001";

    private final ReceiptPdfRenderer renderer = new ReceiptPdfRenderer(new CertificateTextOutliner());

    private final ReceiptPdfRenderer.ReceiptDetails details =
            new ReceiptPdfRenderer.ReceiptDetails("Course Aura Hub", "Jane Learner", "jane@example.com", "pay_123");

    private Order order(String courseTitle, OrderStatus status) {
        Course course = new Course();
        course.setTitle(courseTitle);
        return Order.builder()
                .orderNumber(NUMBER)
                .course(course)
                .listAmount(new BigDecimal("49.00"))
                .discountAmount(new BigDecimal("9.00"))
                .amount(new BigDecimal("40.00"))
                .currency("USD")
                .status(status)
                .provider("member_card")
                .paidAt(Instant.parse("2026-03-09T12:00:00Z"))
                .refundedAt(status == OrderStatus.REFUNDED ? Instant.parse("2026-03-10T12:00:00Z") : null)
                .build();
    }

    private String text(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            return new PDFTextStripper().getText(document);
        }
    }

    @Test
    void render_printsOrderAndBillingDetails() throws Exception {
        assertThat(text(renderer.render(order("Practical Kubernetes", OrderStatus.PAID), details)))
                .contains("Receipt", NUMBER, "Practical Kubernetes", "Jane Learner", "jane@example.com",
                        "Member card", "pay_123", "$40.00");
    }

    @Test
    void render_marksRefundedOrders() throws Exception {
        assertThat(text(renderer.render(order("Practical Kubernetes", OrderStatus.REFUNDED), details)))
                .contains("Refund receipt", "Date refunded");
    }

    @Test
    void render_drawsKhmerCourseTitles() throws Exception {
        assertThat(text(renderer.render(order("វគ្គសិក្សា Git", OrderStatus.PAID), details))).contains(NUMBER);
    }
}
