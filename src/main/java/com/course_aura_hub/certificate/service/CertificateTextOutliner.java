package com.course_aura_hub.certificate.service;

import org.springframework.stereotype.Component;

import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.Path2D;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns text into vector outlines. PDFBox writes characters one by one, but Khmer vowels and subscript
 * consonants have to be arranged around each other, so Java's text layout does the shaping instead.
 * The Khmer font has no Latin letters, so mixed text is split into runs with a font for each.
 */
@Component
public class CertificateTextOutliner {

    private static final String KHMER_REGULAR = "/fonts/NotoSerifKhmer-Regular.ttf";
    private static final String KHMER_BOLD = "/fonts/NotoSerifKhmer-Bold.ttf";
    private static final String LATIN = "/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf";
    private static final FontRenderContext CONTEXT = new FontRenderContext(null, true, true);

    private final Font khmerRegular = load(KHMER_REGULAR);
    private final Font khmerBold = load(KHMER_BOLD);
    private final Font latin = load(LATIN);

    public record Outline(Shape shape, float width) {
    }

    /** @return the outline, or null when a character is missing from every font */
    public Outline outline(String text, float size, boolean bold) {
        Font khmer = (bold ? khmerBold : khmerRegular).deriveFont(size);
        Font other = latin.deriveFont(size);

        Path2D.Float shape = new Path2D.Float();
        float x = 0;
        for (String run : runs(text)) {
            Font font = isKhmer(run.codePointAt(0)) ? khmer : other;
            if (font.canDisplayUpTo(run) != -1) {
                return null;
            }
            char[] chars = run.toCharArray();
            GlyphVector glyphs = font.layoutGlyphVector(CONTEXT, chars, 0, chars.length, Font.LAYOUT_LEFT_TO_RIGHT);
            shape.append(glyphs.getOutline(x, 0), false);
            x += (float) glyphs.getLogicalBounds().getWidth();
        }
        return new Outline(shape, x);
    }

    /** Spaces and marks stay with the run before them, so a run always starts with a real letter. */
    private List<String> runs(String text) {
        List<String> runs = new ArrayList<>();
        StringBuilder run = new StringBuilder();
        Boolean khmerRun = null;
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);
            boolean neutral = Character.isWhitespace(codePoint);
            if (!neutral && khmerRun != null && khmerRun != isKhmer(codePoint)) {
                runs.add(run.toString());
                run.setLength(0);
            }
            if (!neutral) {
                khmerRun = isKhmer(codePoint);
            }
            run.appendCodePoint(codePoint);
        }
        if (!run.isEmpty()) {
            runs.add(run.toString());
        }
        return runs;
    }

    private boolean isKhmer(int codePoint) {
        return (codePoint >= 0x1780 && codePoint <= 0x17FF) || (codePoint >= 0x19E0 && codePoint <= 0x19FF);
    }

    private Font load(String resource) {
        try (InputStream stream = getClass().getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("Missing bundled font " + resource);
            }
            return Font.createFont(Font.TRUETYPE_FONT, stream);
        } catch (IOException | FontFormatException ex) {
            throw new IllegalStateException("Could not load bundled font " + resource, ex);
        }
    }
}
