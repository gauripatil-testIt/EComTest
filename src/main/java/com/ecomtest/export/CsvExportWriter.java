package com.ecomtest.export;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

/**
 * Streams CSV rows directly to the given OutputStream without buffering the
 * full dataset in memory. Call {@link #writeHeader(String...)} once, then
 * {@link #writeRow(Object...)} per row, then {@link #close()} when done.
 */
public class CsvExportWriter implements AutoCloseable {

    private final Writer writer;

    public CsvExportWriter(OutputStream out) {
        this.writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
    }

    public void writeHeader(String... columns) throws IOException {
        writeRow((Object[]) columns);
    }

    public void writeRow(Object... values) throws IOException {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                writer.write(",");
            }
            writer.write(escape(values[i]));
        }
        writer.write("\n");
    }

    public void flush() throws IOException {
        writer.flush();
    }

    @Override
    public void close() throws IOException {
        writer.flush();
        writer.close();
    }

    private String escape(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
