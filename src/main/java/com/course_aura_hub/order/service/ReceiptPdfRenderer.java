package com.course_aura_hub.order.service;

import com.course_aura_hub.certificate.service.CertificateTextOutliner;
import com.course_aura_hub.common.exception.BusinessRuleException;
import com.course_aura_hub.order.model.Order;
import com.course_aura_hub.order.model.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.awt.geom.PathIterator;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Draws a one-page A4 receipt. Text the bundled Latin font can't show, such as a Khmer course title,
 * is drawn as outlines like on certificates.
 */
@Component
@RequiredArgsConstructor
public class ReceiptPdfRenderer {

    private static final String REGULAR_FONT = "/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf";
    private static final String BOLD_FONT = "/org/apache/pdfbox/resources/ttf/LiberationSans-Bold.ttf";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm 'UTC'", Locale.ENGLISH)
            .withZone(ZoneOffset.UTC);

    private static final float MARGIN = 56;
    private static final Color INK = new Color(0x14, 0x21, 0x3D);
    private static final Color MUTED = new Color(0x5C, 0x64, 0x74);
    private static final Color ACCENT = new Color(0x6D, 0x28, 0xD9);
    private static final Color RULE = new Color(0xE2, 0xE8, 0xF0);

    private final CertificateTextOutliner outliner;

    public record ReceiptDetails(String platformName, String learnerName, String learnerEmail, String paymentReference) {
    }

    public byte[] render(Order order, ReceiptDetails details) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            Fonts fonts = new Fonts(load(document, REGULAR_FONT), load(document, BOLD_FONT));
            float width = page.getMediaBox().getWidth();
            float top = page.getMediaBox().getHeight() - MARGIN;

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                Pen pen = new Pen(content, fonts);
                pen.text(details.platformName(), MARGIN, top, 20, true, ACCENT);
                String title = order.getStatus() == OrderStatus.REFUNDED ? "Refund receipt" : "Receipt";
                pen.textRight(title, width - MARGIN, top, 20, true, INK);

                float y = top - 40;
                y = pen.field("Receipt number", order.getOrderNumber(), y);
                y = pen.field("Date paid", format(order.getPaidAt()), y);
                if (order.getRefundedAt() != null) {
                    y = pen.field("Date refunded", format(order.getRefundedAt()), y);
                }
                y = pen.field("Billed to", details.learnerName(), y);
                y = pen.field("Email", details.learnerEmail(), y);
                y = pen.field("Payment method", paymentMethod(order.getProvider()), y);
                if (details.paymentReference() != null) {
                    y = pen.field("Payment reference", details.paymentReference(), y);
                }

                y -= 18;
                pen.rule(MARGIN, width - MARGIN, y);
                y -= 22;
                pen.text("Item", MARGIN, y, 10, true, MUTED);
                pen.textRight("Amount", width - MARGIN, y, 10, true, MUTED);
                y -= 22;
                pen.text(order.getCourse().getTitle(), MARGIN, y, 12, false, INK);
                pen.textRight(money(order.getListAmount(), order.getCurrency()), width - MARGIN, y, 12, false, INK);

                if (order.getDiscountAmount().signum() > 0) {
                    y -= 20;
                    String label = order.getCoupon() != null ? "Coupon " + order.getCoupon().getCode() : "Discount";
                    pen.text(label, MARGIN, y, 11, false, MUTED);
                    pen.textRight("-" + money(order.getDiscountAmount(), order.getCurrency()), width - MARGIN, y, 11, false, MUTED);
                }

                y -= 18;
                pen.rule(MARGIN, width - MARGIN, y);
                y -= 24;
                pen.text("Total paid", MARGIN, y, 13, true, INK);
                pen.textRight(money(order.getAmount(), order.getCurrency()), width - MARGIN, y, 13, true, INK);
                if (order.getStatus() == OrderStatus.REFUNDED) {
                    y -= 20;
                    pen.text("Refunded", MARGIN, y, 12, true, ACCENT);
                    pen.textRight("-" + money(order.getAmount(), order.getCurrency()), width - MARGIN, y, 12, true, ACCENT);
                }

                pen.text("Thank you for learning with " + details.platformName() + ".", MARGIN, MARGIN + 14, 10, false, MUTED);
            }
            document.save(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("Unable to render receipt " + order.getOrderNumber(), ex);
        }
    }

    private static String format(Instant instant) {
        return instant == null ? "—" : DATE.format(instant);
    }

    private static String money(BigDecimal amount, String currency) {
        String code = currency == null ? "USD" : currency.toUpperCase(Locale.ROOT);
        String value = amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
        return "USD".equals(code) ? "$" + value : value + " " + code;
    }

    private static String paymentMethod(String provider) {
        if (provider == null) {
            return "Free";
        }
        return switch (provider) {
            case "member_card" -> "Member card";
            case "fake" -> "Test payment";
            default -> provider.substring(0, 1).toUpperCase(Locale.ROOT) + provider.substring(1);
        };
    }

    private PDFont load(PDDocument document, String resource) throws IOException {
        try (InputStream font = getClass().getResourceAsStream(resource)) {
            if (font == null) {
                throw new IllegalStateException("Missing bundled font " + resource);
            }
            return PDType0Font.load(document, font);
        }
    }

    private record Fonts(PDFont regular, PDFont bold) {
    }

    private final class Pen {
        private final PDPageContentStream content;
        private final Fonts fonts;

        Pen(PDPageContentStream content, Fonts fonts) {
            this.content = content;
            this.fonts = fonts;
        }

        float field(String label, String value, float y) throws IOException {
            text(label, MARGIN, y, 10, false, MUTED);
            text(value == null ? "—" : value, MARGIN + 130, y, 10, false, INK);
            return y - 17;
        }

        void rule(float from, float to, float y) throws IOException {
            content.setStrokingColor(RULE);
            content.setLineWidth(1);
            content.moveTo(from, y);
            content.lineTo(to, y);
            content.stroke();
        }

        void textRight(String text, float right, float y, float size, boolean bold, Color color) throws IOException {
            text(text, right - width(text, size, bold), y, size, bold, color);
        }

        void text(String text, float x, float y, float size, boolean bold, Color color) throws IOException {
            PDFont font = bold ? fonts.bold() : fonts.regular();
            if (canDraw(font, text)) {
                content.beginText();
                content.setNonStrokingColor(color);
                content.setFont(font, size);
                content.newLineAtOffset(x, y);
                content.showText(text);
                content.endText();
                return;
            }
            drawOutline(outlineOf(text, size, bold), color, x, y);
        }

        float width(String text, float size, boolean bold) throws IOException {
            PDFont font = bold ? fonts.bold() : fonts.regular();
            return canDraw(font, text) ? font.getStringWidth(text) / 1000 * size : outlineOf(text, size, bold).width();
        }

        private CertificateTextOutliner.Outline outlineOf(String text, float size, boolean bold) {
            CertificateTextOutliner.Outline outline = outliner.outline(text, size, bold);
            if (outline == null) {
                throw new BusinessRuleException("This receipt uses characters the PDF can't show yet.");
            }
            return outline;
        }

        private boolean canDraw(PDFont font, String text) throws IOException {
            try {
                font.encode(text);
                return true;
            } catch (IllegalArgumentException ex) {
                return false;
            }
        }

        private void drawOutline(CertificateTextOutliner.Outline outline, Color color, float left, float y) throws IOException {
            float[] point = new float[6];
            float lastX = 0;
            float lastY = 0;
            content.setNonStrokingColor(color);
            for (PathIterator path = outline.shape().getPathIterator(null); !path.isDone(); path.next()) {
                switch (path.currentSegment(point)) {
                    case PathIterator.SEG_MOVETO -> {
                        content.moveTo(left + point[0], y - point[1]);
                        lastX = point[0];
                        lastY = point[1];
                    }
                    case PathIterator.SEG_LINETO -> {
                        content.lineTo(left + point[0], y - point[1]);
                        lastX = point[0];
                        lastY = point[1];
                    }
                    case PathIterator.SEG_QUADTO -> {
                        float x1 = lastX + 2f / 3 * (point[0] - lastX);
                        float y1 = lastY + 2f / 3 * (point[1] - lastY);
                        float x2 = point[2] + 2f / 3 * (point[0] - point[2]);
                        float y2 = point[3] + 2f / 3 * (point[1] - point[3]);
                        content.curveTo(left + x1, y - y1, left + x2, y - y2, left + point[2], y - point[3]);
                        lastX = point[2];
                        lastY = point[3];
                    }
                    case PathIterator.SEG_CUBICTO -> {
                        content.curveTo(left + point[0], y - point[1], left + point[2], y - point[3],
                                left + point[4], y - point[5]);
                        lastX = point[4];
                        lastY = point[5];
                    }
                    default -> content.closePath();
                }
            }
            content.fill();
        }
    }
}
