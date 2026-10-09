package com.ecomtest.controller;

import com.ecomtest.dto.ProductRequest;
import com.ecomtest.dto.ProductResponse;
import com.ecomtest.entity.Product;
import com.ecomtest.entity.ProductStatus;
import com.ecomtest.service.ProductService;
import com.ecomtest.service.export.ExportFormat;
import com.ecomtest.service.export.ProductExportService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;
    private final ProductExportService productExportService;

    public ProductController(ProductService productService, ProductExportService productExportService) {
        this.productService = productService;
        this.productExportService = productExportService;
    }

    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        Product product = productService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ProductResponse.from(product));
    }

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable Long id) {
        return ProductResponse.from(productService.get(id));
    }

    @GetMapping
    public List<ProductResponse> list(@RequestParam(required = false) ProductStatus status) {
        return productService.list(status).stream().map(ProductResponse::from).toList();
    }

    @PreAuthorize("hasAuthority('EXPORT_PRODUCTS') or hasAuthority('EXPORT_ORDERS')")
    @GetMapping("/export")
    public void exportProducts(@RequestParam String format,
                                @RequestParam(required = false) ProductStatus status,
                                HttpServletResponse response) throws IOException {
        ExportFormat exportFormat = ExportFormat.fromParam(format);
        String filename = productExportService.buildFilename(exportFormat);
        response.setContentType(exportFormat == ExportFormat.CSV
                ? "text/csv"
                : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        productExportService.writeTo(status, exportFormat, response.getOutputStream());
    }

    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return ProductResponse.from(productService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
