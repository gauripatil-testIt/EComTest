package com.ecomtest.service;

import com.ecomtest.entity.Product;
import com.ecomtest.entity.ProductStatus;
import com.ecomtest.export.ExportFormat;
import com.ecomtest.repository.ProductRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

@Service
public class ProductExportService {

    private static final String[] HEADERS = {"id", "name", "sku", "price", "stock", "status"};
    private static final int XLSX_ROW_ACCESS_WINDOW = 100;

    private final ProductRepository productRepository;

    public ProductExportService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public void write(OutputStream outputStream, ExportFormat format, ProductStatus status) throws IOException {
        switch (format) {
            case CSV -> writeCsv(outputStream, status);
            case XLSX -> writeXlsx(outputStream, status);
        }
    }

    private void writeCsv(OutputStream outputStream, ProductStatus status) throws IOException {
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
        writer.write(String.join(",", HEADERS));
        writer.write("\n");
        try (Stream<Product> products = productRepository.streamForExport(status)) {
            for (Product product : (Iterable<Product>) products::iterator) {
                writer.write(csvRow(product));
                writer.write("\n");
            }
        }
        writer.flush();
    }

    private String csvRow(Product product) {
        return String.join(",",
                csvValue(product.getId()),
                csvValue(product.getName()),
                csvValue(product.getSku()),
                csvValue(product.getPrice()),
                csvValue(product.getStock()),
                csvValue(product.getStatus()));
    }

    private String csvValue(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString();
        if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
            text = "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private void writeXlsx(OutputStream outputStream, ProductStatus status) throws IOException {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(XLSX_ROW_ACCESS_WINDOW)) {
            SXSSFSheet sheet = workbook.createSheet("Products");

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                headerRow.createCell(i).setCellValue(HEADERS[i]);
            }

            int rowIndex = 1;
            try (Stream<Product> products = productRepository.streamForExport(status)) {
                for (Product product : (Iterable<Product>) products::iterator) {
                    Row row = sheet.createRow(rowIndex++);
                    writeXlsxRow(row, product);
                }
            }

            workbook.write(outputStream);
            workbook.dispose();
        }
    }

    private void writeXlsxRow(Row row, Product product) {
        row.createCell(0).setCellValue(product.getId() == null ? 0d : product.getId().doubleValue());
        row.createCell(1).setCellValue(product.getName());
        row.createCell(2).setCellValue(product.getSku());

        Cell priceCell = row.createCell(3);
        if (product.getPrice() != null) {
            priceCell.setCellValue(product.getPrice().doubleValue());
        }

        Cell stockCell = row.createCell(4);
        if (product.getStock() != null) {
            stockCell.setCellValue(product.getStock());
        }

        row.createCell(5).setCellValue(product.getStatus() == null ? "" : product.getStatus().name());
    }
}
