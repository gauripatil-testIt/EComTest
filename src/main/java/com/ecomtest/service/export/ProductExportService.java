package com.ecomtest.service.export;

import com.ecomtest.entity.Product;
import com.ecomtest.entity.ProductStatus;
import com.ecomtest.repository.ProductRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.stream.Stream;

@Service
public class ProductExportService {

    private final ProductRepository productRepository;

    public ProductExportService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public void writeTo(ProductStatus status, ExportFormat format, OutputStream out) {
        switch (format) {
            case CSV -> writeCsv(status, out);
            case XLSX -> writeXlsx(status, out);
        }
    }

    public String buildFilename(ExportFormat format) {
        String extension = format == ExportFormat.CSV ? "csv" : "xlsx";
        return "products-export-" + Instant.now().toEpochMilli() + "." + extension;
    }

    private void writeCsv(ProductStatus status, OutputStream out) {
        try {
            Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
            writer.write("id,name,sku,price,stock,status\n");
            try (Stream<Product> products = productRepository.streamForExport(status)) {
                products.forEach(product -> {
                    try {
                        writer.write(toCsvRow(product));
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }
            writer.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String toCsvRow(Product product) {
        return String.join(",",
                csvEscape(String.valueOf(product.getId())),
                csvEscape(product.getName()),
                csvEscape(product.getSku()),
                csvEscape(product.getPrice().toPlainString()),
                csvEscape(String.valueOf(product.getStock())),
                csvEscape(product.getStatus().name())
        ) + "\n";
    }

    private String csvEscape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private void writeXlsx(ProductStatus status, OutputStream out) {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            Sheet sheet = workbook.createSheet("Products");
            Row header = sheet.createRow(0);
            String[] columns = {"id", "name", "sku", "price", "stock", "status"};
            for (int i = 0; i < columns.length; i++) {
                header.createCell(i).setCellValue(columns[i]);
            }

            int[] rowNum = {1};
            try (Stream<Product> products = productRepository.streamForExport(status)) {
                products.forEach(product -> {
                    Row row = sheet.createRow(rowNum[0]++);
                    row.createCell(0).setCellValue(product.getId());
                    row.createCell(1).setCellValue(product.getName());
                    row.createCell(2).setCellValue(product.getSku());
                    row.createCell(3).setCellValue(product.getPrice().doubleValue());
                    row.createCell(4).setCellValue(product.getStock());
                    row.createCell(5).setCellValue(product.getStatus().name());
                });
            }

            workbook.write(out);
            workbook.dispose();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
