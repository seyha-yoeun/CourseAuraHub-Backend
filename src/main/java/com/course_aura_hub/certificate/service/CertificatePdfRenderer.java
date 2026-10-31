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
import java.awt.geom.PathIterator;
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

    private static final String[] KHMER_MONTHS = {
            "មករា", "កុម្ភៈ", "មីនា", "មេសា", "ឧសភា", "មិថុនា", "កក្កដា", "សីហា", "កញ្ញា", "តុលា", "វិច្ឆិកា", "ធ្នូ"
    };

    private record Wording(
            String heading,
            String certifies,
            String completed,
            String instructor,
            String date,
            String scan,
            String certificateId,
            String verifyAt
    ) {
    }

    private static final Wording ENGLISH = new Wording(
            "CERTIFICATE OF COMPLETION",
            "This is to certify that",
            "has successfully completed every lesson of the course",
            "COURSE INSTRUCTOR",
            "DATE OF COMPLETION",
            "SCAN TO VERIFY",
            "Certificate ID  ",
            "Verify this certificate at "
    );

    private static final Wording KHMER = new Wording(
            "វិញ្ញាបនបត្របញ្ចប់វគ្គសិក្សា",
            "សូមបញ្ជាក់ថា",
            "បានបញ្ចប់គ្រប់មេរៀនទាំងអស់នៃវគ្គសិក្សា",
            "គ្រូបង្រៀន",
            "កាលបរិច្ឆេទបញ្ចប់",
            "ស្កេនដើម្បីផ្ទៀងផ្ទាត់",
            "លេខវិញ្ញាបនបត្រ  ",
            "ផ្ទៀងផ្ទាត់វិញ្ញាបនបត្រនេះនៅ "
    );

    private final CertificateProperties properties;
    private final CertificateTextOutliner outliner;

    public byte[] render(CourseCertificate certificate) {
        return render(certificate, "en");
    }

    /**
     * @param language {@code km} for Khmer wording, anything else for English
     * @throws BusinessRuleException when a name or title uses a script the bundled fonts cannot draw
     */
    public byte[] render(CourseCertificate certificate, String language) {
        boolean khmer = "km".equalsIgnoreCase(language);
        Wording wording = khmer ? KHMER : ENGLISH;
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(WIDTH, HEIGHT));
            document.addPage(page);

            PDDocumentInformation info = document.getDocumentInformation();
            info.setTitle("Certificate of Completion - " + certificate.getCourseTitle());
            info.setCreator("CourseAuraHub");

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                drawFrame(content);
                drawBrand(content);
                drawHeading(document, content, wording);
                drawBody(document, content, certificate, wording);
                drawFooter(document, content, certificate, wording, khmer);
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

        drawCentered(null, content, "COURSEAURAHUB ACADEMY", SANS_BOLD, 8.5f, 2.6f, NAVY, CENTER, 503);
    }

    private void drawHeading(PDDocument document, PDPageContentStream content, Wording wording) throws IOException {
        drawCentered(document, content, wording.heading(), SERIF_BOLD, 28, 2.5f, NAVY, CENTER, 459);

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

    private void drawBody(PDDocument document, PDPageContentStream content, CourseCertificate certificate, Wording wording) throws IOException {
        drawCentered(document, content, wording.certifies(), SERIF_ITALIC, 13, 0, MUTED, CENTER, 412);

        String name = singleLine(certificate.getLearnerName());
        drawCentered(document, content, name, SERIF_BOLD, fitSize(document, name, SERIF_BOLD, 38, 18, 640), 0, NAVY, CENTER, 366);

        content.setStrokingColor(GOLD);
        content.setLineWidth(0.75f);
        content.moveTo(CENTER - 210, 354);
        content.lineTo(CENTER + 210, 354);
        content.stroke();

        drawCentered(document, content, wording.completed(), SERIF_ITALIC, 13, 0, MUTED, CENTER, 330);

        String title = singleLine(certificate.getCourseTitle());
        float size = 22;
        List<String> lines = wrap(document, title, SERIF_BOLD, size, 620);
        while (lines.size() > 2 && size > 12) {
            size -= 1;
            lines = wrap(document, title, SERIF_BOLD, size, 620);
        }
        if (lines.size() > 2) {
            lines = List.of(lines.get(0), lines.get(1) + "...");
        }
        float y = lines.size() == 1 ? 293 : 298;
        for (String line : lines) {
            drawCentered(document, content, line, SERIF_BOLD, size, 0, NAVY, CENTER, y);
            y -= size + 6;
        }
    }

    private void drawFooter(
            PDDocument document,
            PDPageContentStream content,
            CourseCertificate certificate,
            Wording wording,
            boolean khmer
    ) throws IOException {
        drawSignatory(document, content, singleLine(certificate.getInstructor()), wording.instructor(), 175);
        drawSignatory(document, content, completionDate(certificate, khmer), wording.date(), 595);
        drawSeal(content, CENTER, 136);

        String url = verificationUrl(certificate);
        drawQrCode(content, url, 704, 118, 64);
        drawCentered(document, content, wording.scan(), SANS_BOLD, 5.5f, 0.8f, MUTED, 736, 108);

        drawCentered(document, content, wording.certificateId() + certificate.getCertificateNumber(), SERIF_BOLD, 9.5f, 0.3f, NAVY, CENTER, 62);
        drawCentered(document, content, wording.verifyAt() + url, SERIF, 8.5f, 0, MUTED, CENTER, 49);
    }

    private void drawSignatory(PDDocument document, PDPageContentStream content, String value, String label, float x) throws IOException {
        drawCentered(document, content, value, SERIF_BOLD, fitSize(document, value, SERIF_BOLD, 13, 8, 180), 0, NAVY, x, 134);
        content.setStrokingColor(NAVY);
        content.setLineWidth(0.6f);
        content.moveTo(x - 92, 126);
        content.lineTo(x + 92, 126);
        content.stroke();
        drawCentered(document, content, label, SANS_BOLD, 6.5f, 1, MUTED, x, 114);
    }

    private String completionDate(CourseCertificate certificate, boolean khmer) {
        if (!khmer) {
            return DATE.format(certificate.getIssuedAt());
        }
        var date = certificate.getIssuedAt().atZone(ZoneOffset.UTC).toLocalDate();
        return "ថ្ងៃទី" + khmerDigits(date.getDayOfMonth())
                + " ខែ" + KHMER_MONTHS[date.getMonthValue() - 1]
                + " ឆ្នាំ" + khmerDigits(date.getYear());
    }

    private String khmerDigits(int number) {
        StringBuilder digits = new StringBuilder();
        String.valueOf(number).chars().forEach(digit -> digits.append((char) (0x17E0 + digit - '0')));
        return digits.toString();
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

    /** Draws with a real PDF font when one covers the text, otherwise as shaped outlines. */
    private void drawCentered(
            PDDocument document,
            PDPageContentStream content,
            String text,
            PDFont preferred,
            float size,
            float spacing,
            Color color,
            float x,
            float y
    ) throws IOException {
        PDFont font = fontFor(document, preferred, text);
        if (font == null) {
            drawOutline(content, outline(text, preferred, size), color, x, y);
            return;
        }

        float width = font.getStringWidth(text) / 1000 * size + spacing * (text.length() - 1);
        content.beginText();
        content.setFont(font, size);
        content.setCharacterSpacing(spacing);
        content.setNonStrokingColor(color);
        content.newLineAtOffset(x - width / 2, y);
        content.showText(text);
        content.endText();
    }

    private void drawOutline(PDPageContentStream content, CertificateTextOutliner.Outline outline, Color color, float x, float y) throws IOException {
        float left = x - outline.width() / 2;
        float[] point = new float[6];
        float lastX = 0;
        float lastY = 0;

        content.setNonStrokingColor(color);
        for (PathIterator path = outline.shape().getPathIterator(null); !path.isDone(); path.next()) {
            int type = path.currentSegment(point);
            // Java's y axis points down and PDF's up
            switch (type) {
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
                    // PDF has only cubic curves, so the quadratic is raised to an equivalent one
                    float x1 = lastX + 2f / 3 * (point[0] - lastX);
                    float y1 = lastY + 2f / 3 * (point[1] - lastY);
                    float x2 = point[2] + 2f / 3 * (point[0] - point[2]);
                    float y2 = point[3] + 2f / 3 * (point[1] - point[3]);
                    content.curveTo(left + x1, y - y1, left + x2, y - y2, left + point[2], y - point[3]);
                    lastX = point[2];
                    lastY = point[3];
                }
                case PathIterator.SEG_CUBICTO -> {
                    content.curveTo(left + point[0], y - point[1], left + point[2], y - point[3], left + point[4], y - point[5]);
                    lastX = point[4];
                    lastY = point[5];
                }
                default -> content.closePath();
            }
        }
        content.fill();
    }

    private CertificateTextOutliner.Outline outline(String text, PDFont preferred, float size) {
        CertificateTextOutliner.Outline outline = outliner.outline(text, size, preferred == SERIF_BOLD || preferred == SANS_BOLD);
        if (outline == null) {
            throw new BusinessRuleException(
                    "This certificate uses characters the PDF can't show yet. Use Print on the certificate page instead.");
        }
        return outline;
    }

    private String verificationUrl(CourseCertificate certificate) {
        return properties.verifyUrl().replaceAll("/+$", "") + "/" + certificate.getCertificateNumber();
    }

    /** @return a PDF font that covers the text, or null when it needs outlines */
    private PDFont fontFor(PDDocument document, PDFont preferred, String text) throws IOException {
        if (canDraw(preferred, text)) {
            return preferred;
        }
        try (InputStream font = getClass().getResourceAsStream(UNICODE_FONT)) {
            if (font == null) {
                throw new IllegalStateException("Missing bundled font " + UNICODE_FONT);
            }
            PDFont unicode = PDType0Font.load(document, font);
            return canDraw(unicode, text) ? unicode : null;
        }
    }

    private boolean canDraw(PDFont font, String text) throws IOException {
        try {
            font.encode(text);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private float fitSize(PDDocument document, String text, PDFont font, float largest, float smallest, float maxWidth) throws IOException {
        float size = largest;
        while (size > smallest && textWidth(document, text, font, size) > maxWidth) {
            size -= 1;
        }
        return size;
    }

    private List<String> wrap(PDDocument document, String text, PDFont font, float size, float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && textWidth(document, candidate, font, size) > maxWidth) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        lines.add(line.toString());
        return lines;
    }

    private float textWidth(PDDocument document, String text, PDFont preferred, float size) throws IOException {
        PDFont font = fontFor(document, preferred, text);
        return font == null ? outline(text, preferred, size).width() : font.getStringWidth(text) / 1000 * size;
    }

    private String singleLine(String text) {
        return text.replaceAll("\\s+", " ").strip();
    }
}
