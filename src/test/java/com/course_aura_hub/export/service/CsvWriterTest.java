package com.course_aura_hub.export.service;

import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CsvWriterTest {

    @Test
    void quotesCommasQuotesAndLineBreaks() {
        assertThat(CsvWriter.cell("plain")).isEqualTo("plain");
        assertThat(CsvWriter.cell("a,b")).isEqualTo("\"a,b\"");
        assertThat(CsvWriter.cell("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
        assertThat(CsvWriter.cell("two\nlines")).isEqualTo("\"two\nlines\"");
        assertThat(CsvWriter.cell(null)).isEmpty();
    }

    @Test
    void neutralisesSpreadsheetFormulas_butNotNumbers() {
        assertThat(CsvWriter.cell("=HYPERLINK(\"http://evil\")")).startsWith("\"'=HYPERLINK");
        assertThat(CsvWriter.cell("+1 555")).isEqualTo("'+1 555");
        assertThat(CsvWriter.cell("@SUM(A1)")).isEqualTo("'@SUM(A1)");
        assertThat(CsvWriter.cell(new BigDecimal("-12.50"))).isEqualTo("-12.50");
    }

    @Test
    void writesRowsWithCrLf() {
        StringWriter out = new StringWriter();
        new CsvWriter(out).row("a", 1, null).row("b", 2, "x");
        assertThat(out.toString()).isEqualTo("a,1,\r\nb,2,x\r\n");
    }
}
