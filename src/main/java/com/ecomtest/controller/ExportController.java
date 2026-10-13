package com.ecomtest.controller;

import com.ecomtest.service.ExportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/export")
public class ExportController {

    private final ExportService exportService;

    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping(value = "/orders", produces = "text/csv")
    public ResponseEntity<String> exportOrders() {
        String csv = exportService.ordersCsv();
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"orders.csv\"")
                .body(csv);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping(value = "/products", produces = "text/csv")
    public ResponseEntity<String> exportProducts() {
        String csv = exportService.productsCsv();
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"products.csv\"")
                .body(csv);
    }
}
