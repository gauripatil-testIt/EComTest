package com.ecomtest.service;

import com.ecomtest.entity.Product;
import com.ecomtest.entity.ProductStatus;
import com.ecomtest.export.CsvExportWriter;
import com.ecomtest.export.ExportFormat;
import com.ecomtest.export.XlsxExportWriter;
import com.ecomtest.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;

@Service
public class ProductExportService {

    private static final int PAGE_SIZE = 500;
    private static final String[] COLUMNS = {"id", "name", "sku", "price", "stock", "status"};

    private final ProductRepository productRepository;

    public ProductExportService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public void export(ProductStatus status, ExportFormat format, OutputStream out) throws IOException {
        if (format == ExportFormat.CSV) {
            exportCsv(status, out);
        } else {
            exportXlsx(status, out);
        }
    }

    private void exportCsv(ProductStatus status, OutputStream out) throws IOException {
        try (CsvExportWriter writer = new CsvExportWriter(out)) {
            writer.writeHeader(COLUMNS);
            int pageNumber = 0;
            Page<Product> page;
            do {
                page = fetchPage(status, pageNumber);
                for (Product product : page.getContent()) {
                    writer.writeRow(
                            product.getId(),
                            product.getName(),
                            product.getSku(),
                            product.getPrice(),
                            product.getStock(),
                            product.getStatus());
                }
                writer.flush();
                pageNumber++;
            } while (page.hasNext());
        }
    }

    private void exportXlsx(ProductStatus status, OutputStream out) throws IOException {
        try (XlsxExportWriter writer = new XlsxExportWriter("Products")) {
            writer.writeHeader(COLUMNS);
            int pageNumber = 0;
            Page<Product> page;
            do {
                page = fetchPage(status, pageNumber);
                for (Product product : page.getContent()) {
                    writer.writeRow(
                            product.getId(),
                            product.getName(),
                            product.getSku(),
                            product.getPrice(),
                            product.getStock(),
                            product.getStatus() == null ? null : product.getStatus().name());
                }
                pageNumber++;
            } while (page.hasNext());
            writer.write(out);
        }
    }

    private Page<Product> fetchPage(ProductStatus status, int pageNumber) {
        PageRequest pageRequest = PageRequest.of(pageNumber, PAGE_SIZE);
        if (status == null) {
            return productRepository.findAll(pageRequest);
        }
        return productRepository.findByStatus(status, pageRequest);
    }
}
