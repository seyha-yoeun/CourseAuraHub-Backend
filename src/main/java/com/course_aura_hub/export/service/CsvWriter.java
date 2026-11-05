package com.course_aura_hub.export.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.temporal.TemporalAccessor;

/**
 * RFC 4180 CSV rows. Cells that a spreadsheet would run as a formula (starting with =, +, -, @, tab or carriage
 * return) get a leading apostrophe, so an exported learner name can't execute anything.
 */
public final class CsvWriter {

    private final Writer out;

    public CsvWriter(Writer out) {
        this.out = out;
    }

    public CsvWriter row(Object... cells) {
        try {
            for (int i = 0; i < cells.length; i++) {
                if (i > 0) {
                    out.write(',');
                }
                out.write(cell(cells[i]));
            }
            out.write("\r\n");
            return this;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    static String cell(Object value) {
        if (value == null) {
            return "";
        }
        String text = switch (value) {
            case BigDecimal decimal -> decimal.toPlainString();
            case Timestamp timestamp -> timestamp.toInstant().toString();
            case TemporalAccessor temporal -> temporal.toString();
            default -> value.toString();
        };
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0 && !(value instanceof Number)) {
            text = "'" + text;
        }
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            text = "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
