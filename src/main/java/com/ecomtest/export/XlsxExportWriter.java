package com.ecomtest.export;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;

/**
 * Streams rows into a SXSSFWorkbook (POI's streaming xlsx implementation),
 * keeping only a bounded window of rows in memory at a time and flushing the
 * rest to temporary disk storage. Numeric values are written as numeric
 * cells so consumers see real numbers rather than text.
 */
public class XlsxExportWriter implements AutoCloseable {

    private static final int ROW_ACCESS_WINDOW_SIZE = 500;

    private final SXSSFWorkbook workbook;
    private final SXSSFSheet sheet;
    private int rowIndex = 0;

    public XlsxExportWriter(String sheetName) {
        this.workbook = new SXSSFWorkbook(ROW_ACCESS_WINDOW_SIZE);
        this.workbook.setCompressTempFiles(true);
        this.sheet = workbook.createSheet(sheetName);
    }

    public void writeHeader(String... columns) {
        Row row = sheet.createRow(rowIndex++);
        for (int i = 0; i < columns.length; i++) {
            Cell cell = row.createCell(i, org.apache.poi.ss.usermodel.CellType.STRING);
            cell.setCellValue(columns[i]);
        }
    }

    public void writeRow(Object... values) {
        Row row = sheet.createRow(rowIndex++);
        for (int i = 0; i < values.length; i++) {
            Object value = values[i];
            Cell cell;
            if (value instanceof Number number) {
                cell = row.createCell(i, org.apache.poi.ss.usermodel.CellType.NUMERIC);
                if (value instanceof BigDecimal bigDecimal) {
                    cell.setCellValue(bigDecimal.doubleValue());
                } else {
                    cell.setCellValue(number.doubleValue());
                }
            } else {
                cell = row.createCell(i, org.apache.poi.ss.usermodel.CellType.STRING);
                cell.setCellValue(value == null ? "" : String.valueOf(value));
            }
        }
    }

    public void write(OutputStream out) throws IOException {
        workbook.write(out);
    }

    @Override
    public void close() throws IOException {
        workbook.dispose();
        workbook.close();
    }
}
