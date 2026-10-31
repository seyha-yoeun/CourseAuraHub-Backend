package com.course_aura_hub.certificate.service;

import com.course_aura_hub.certificate.config.CertificateProperties;
import com.course_aura_hub.certificate.model.CourseCertificate;
import com.course_aura_hub.common.exception.BusinessRuleException;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Draws a certificate as an A4 landscape PDF. Everything is vector, so it prints sharply at any size.
 */
@Component
@RequiredArgsConstructor
public class CertificatePdfRenderer {

    private static final float WIDTH = PDRectangle.A4.getHeight();
    private static final float HEIGHT = PDRectangle.A4.getWidth();
    private static final float CENTER = WIDTH / 2;

    private static final Color PAPER = new Color(0xFD, 0xFB, 0xF5);
    private static final Color NAVY = new Color(0x14, 0x21, 0x3D);
    private static final Color GOLD = new Color(0xB0, 0x8D, 0x3C);
    private static final Color MUTED = new Color(0x5C, 0x64, 0x74);

    private static final PDFont SERIF = new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);
    private static final PDFont SERIF_BOLD = new PDType1Font(Standard14Fonts.FontName.TIMES_BOLD);
    private static final PDFont SERIF_ITALIC = new PDType1Font(Standard14Fonts.FontName.TIMES_ITALIC);
    private static final PDFont SANS_BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    // Shipped inside PDFBox. Reaches names and titles beyond Western European, such as Cyrillic and Greek
    private static final String UNICODE_FONT = "/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf";

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH).withZone(ZoneOffset.UTC);

    private final CertificateProperties properties;

    /**
     * @throws BusinessRuleException when a name or title uses a script the embedded fonts cannot draw
     */
    public byte[] render(CourseCertificate certificate) {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(WIDTH, HEIGHT));
            document.addPage(page);

            PDDocumentInformation info = document.getDocumentInformation();
            info.setTitle("Certificate of Completion - " + certificate.getCourseTitle());
            info.setCreator("CourseAuraHub");

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                drawFrame(content);
                drawBrand(content);
                drawHeading(content);
                drawBody(document, content, certificate);
                drawFooter(document, content, certificate);
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Could not render certificate " + certificate.getCertificateNumber(), ex);
        }
    }

    private void drawFrame(PDPageContentStream content) throws IOException {
        content.setNonStrokingColor(PAPER);
        content.addRect(0, 0, WIDTH, HEIGHT);
        content.fill();

        content.setStrokingColor(NAVY);
        content.setLineWidth(2.5f);
        content.addRect(18, 18, WIDTH - 36, HEIGHT - 36);
        content.stroke();

        content.setStrokingColor(GOLD);
        content.setLineWidth(0.75f);
        content.addRect(25, 25, WIDTH - 50, HEIGHT - 50);
        content.stroke();

        content.setLineWidth(2);
        for (float[] corner : new float[][]{{31, 31, 1, 1}, {WIDTH - 31, 31, -1, 1}, {31, HEIGHT - 31, 1, -1}, {WIDTH - 31, HEIGHT - 31, -1, -1}}) {
            content.moveTo(corner[0] + 34 * corner[2], corner[1]);
            content.lineTo(corner[0], corner[1]);
            content.lineTo(corner[0], corner[1] + 34 * corner[3]);
            content.stroke();
        }
    }

    private void drawBrand(PDPageContentStream content) throws IOException {
        float y = 533;
        float radius = 15;
        content.setNonStrokingColor(NAVY);
        drawPolygon(content, CENTER, y, new float[]{radius}, 6, (float) (Math.PI / 2));
        content.fill();
        content.setNonStrokingColor(GOLD);
        drawPolygon(content, CENTER, y, new float[]{radius * 0.5f}, 6, (float) (Math.PI / 2));
        content.fill();

        drawCentered(content, "COURSEAURAHUB ACADEMY", SANS_BOLD, 8.5f, 2.6f, NAVY, CENTER, 503);
    }

    private void drawHeading(PDPageContentStream content) throws IOException {
        drawCentered(content, "CERTIFICATE OF COMPLETION", SERIF_BOLD, 28, 2.5f, NAVY, CENTER, 459);

        content.setStrokingColor(GOLD);
        content.setLineWidth(1);
        content.moveTo(CENTER - 90, 442);
        content.lineTo(CENTER - 8, 442);
        content.moveTo(CENTER + 8, 442);
        content.lineTo(CENTER + 90, 442);
        content.stroke();
        content.setNonStrokingColor(GOLD);
        drawPolygon(content, CENTER, 442, new float[]{4}, 4, 0);
        content.fill();
    }

    private void drawBody(PDDocument document, PDPageContentStream content, CourseCertificate certificate) throws IOException {
        drawCentered(content, "This is to certify that", SERIF_ITALIC, 13, 0, MUTED, CENTER, 412);

        String name = singleLine(certificate.getLearnerName());
        PDFont nameFont = fontFor(document, SERIF_BOLD, name);
        drawCentered(content, name, nameFont, fitSize(name, nameFont, 38, 18, 640), 0, NAVY, CENTER, 366);

        content.setStrokingColor(GOLD);
        content.setLineWidth(0.75f);
        content.moveTo(CENTER - 210, 354);
        content.lineTo(CENTER + 210, 354);
        content.stroke();

        drawCentered(content, "has successfully completed every lesson of the course", SERIF_ITALIC, 13, 0, MUTED, CENTER, 330);

        String title = singleLine(certificate.getCourseTitle());
        PDFont titleFont = fontFor(document, SERIF_BOLD, title);
        float size = 22;
        List<String> lines = wrap(title, titleFont, size, 620);
        while (lines.size() > 2 && size > 12) {
            size -= 1;
            lines = wrap(title, titleFont, size, 620);
        }
        if (lines.size() > 2) {
            lines = List.of(lines.get(0), lines.get(1) + "...");
        }
        float y = lines.size() == 1 ? 293 : 298;
        for (String line : lines) {
            drawCentered(content, line, titleFont, size, 0, NAVY, CENTER, y);
            y -= size + 6;
        }
    }

    private void drawFooter(PDDocument document, PDPageContentStream content, CourseCertificate certificate) throws IOException {
        String instructor = singleLine(certificate.getInstructor());
        PDFont instructorFont = fontFor(document, SERIF_BOLD, instructor);
        drawSignatory(content, instructor, instructorFont, "COURSE INSTRUCTOR", 175);
        drawSignatory(content, DATE.format(certificate.getIssuedAt()), SERIF_BOLD, "DATE OF COMPLETION", 595);
        drawSeal(content, CENTER, 136);

        String url = verificationUrl(certificate);
        drawQrCode(content, url, 704, 118, 64);
        drawCentered(content, "SCAN TO VERIFY", SANS_BOLD, 5.5f, 0.8f, MUTED, 736, 108);

        drawCentered(content, "Certificate ID  " + certificate.getCertificateNumber(), SERIF_BOLD, 9.5f, 0.3f, NAVY, CENTER, 62);
        drawCentered(content, "Verify this certificate at " + url, SERIF, 8.5f, 0, MUTED, CENTER, 49);
    }

    private void drawSignatory(PDPageContentStream content, String value, PDFont font, String label, float x) throws IOException {
        drawCentered(content, value, font, fitSize(value, font, 13, 8, 180), 0, NAVY, x, 134);
        content.setStrokingColor(NAVY);
        content.setLineWidth(0.6f);
        content.moveTo(x - 92, 126);
        content.lineTo(x + 92, 126);
        content.stroke();
        drawCentered(content, label, SANS_BOLD, 6.5f, 1, MUTED, x, 114);
    }

    private void drawSeal(PDPageContentStream content, float x, float y) throws IOException {
        content.setNonStrokingColor(GOLD);
        drawPolygon(content, x, y, new float[]{38, 34}, 48, 0);
        content.fill();
        content.setStrokingColor(PAPER);
        content.setLineWidth(1);
        drawPolygon(content, x, y, new float[]{29}, 64, 0);
        content.stroke();
        content.setStrokingColor(PAPER);
        content.setLineWidth(4);
        content.moveTo(x - 12, y);
        content.lineTo(x - 3, y - 10);
        content.lineTo(x + 14, y + 11);
        content.stroke();
    }

    /** Draws dark modules as rectangles, keeping the code vector. */
    private void drawQrCode(PDPageContentStream content, String url, float x, float y, float size) throws IOException {
        BitMatrix matrix;
        try {
            matrix = new QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 0, 0, Map.of(
                    EncodeHintType.MARGIN, 0,
                    EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M));
        } catch (WriterException ex) {
            throw new IllegalStateException("Could not encode verification URL", ex);
        }

        float module = size / matrix.getWidth();
        content.setNonStrokingColor(NAVY);
        for (int row = 0; row < matrix.getHeight(); row++) {
            for (int column = 0; column < matrix.getWidth(); column++) {
                if (matrix.get(column, row)) {
                    content.addRect(x + column * module, y + size - (row + 1) * module, module, module);
                }
            }
        }
        content.fill();
    }

    /** Traces a shape around a center, cycling through the radii: one radius gives a polygon, two a star. */
    private void drawPolygon(PDPageContentStream content, float x, float y, float[] radii, int points, float rotation) throws IOException {
        for (int i = 0; i < points; i++) {
            double angle = rotation + 2 * Math.PI * i / points;
            float radius = radii[i % radii.length];
            float pointX = x + radius * (float) Math.cos(angle);
            float pointY = y + radius * (float) Math.sin(angle);
            if (i == 0) {
                content.moveTo(pointX, pointY);
            } else {
                content.lineTo(pointX, pointY);
            }
        }
        content.closePath();
    }

    private void drawCentered(
            PDPageContentStream content,
            String text,
            PDFont font,
            float size,
            float spacing,
            Color color,
            float x,
            float y
    ) throws IOException {
        float width = textWidth(text, font, size) + spacing * (text.length() - 1);
        content.beginText();
        content.setFont(font, size);
        content.setCharacterSpacing(spacing);
        content.setNonStrokingColor(color);
        content.newLineAtOffset(x - width / 2, y);
        content.showText(text);
        content.endText();
    }

    private String verificationUrl(CourseCertificate certificate) {
        return properties.verifyUrl().replaceAll("/+$", "") + "/" + certificate.getCertificateNumber();
    }

    private PDFont fontFor(PDDocument document, PDFont preferred, String text) throws IOException {
        if (canDraw(preferred, text)) {
            return preferred;
        }
        try (InputStream font = getClass().getResourceAsStream(UNICODE_FONT)) {
            if (font == null) {
                throw new IllegalStateException("Missing bundled font " + UNICODE_FONT);
            }
            PDFont unicode = PDType0Font.load(document, font);
            if (canDraw(unicode, text)) {
                return unicode;
            }
        }
        throw new BusinessRuleException(
                "This certificate uses characters the PDF can't show yet. Use Print on the certificate page instead.");
    }

    private boolean canDraw(PDFont font, String text) throws IOException {
        try {
            font.encode(text);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private float fitSize(String text, PDFont font, float largest, float smallest, float maxWidth) throws IOException {
        float size = largest;
        while (size > smallest && textWidth(text, font, size) > maxWidth) {
            size -= 1;
        }
        return size;
    }

    private List<String> wrap(String text, PDFont font, float size, float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && textWidth(candidate, font, size) > maxWidth) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        lines.add(line.toString());
        return lines;
    }

    private float textWidth(String text, PDFont font, float size) throws IOException {
        return font.getStringWidth(text) / 1000 * size;
    }

    private String singleLine(String text) {
        return text.replaceAll("\\s+", " ").strip();
    }
}
