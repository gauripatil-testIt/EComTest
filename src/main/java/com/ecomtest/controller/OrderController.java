package com.ecomtest.controller;

import com.ecomtest.dto.OrderRequest;
import com.ecomtest.dto.OrderResponse;
import com.ecomtest.dto.PageResponse;
import com.ecomtest.entity.Order;
import com.ecomtest.entity.OrderStatus;
import com.ecomtest.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody OrderRequest request) {
        Order order = orderService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(order));
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable Long id) {
        return OrderResponse.from(orderService.get(id));
    }

    @GetMapping
    public PageResponse<OrderResponse> list(Pageable pageable,
                                             @RequestParam(required = false) OrderStatus status,
                                             @RequestParam(required = false) String customerName,
                                             @RequestParam(required = false) Long productId,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        Instant from = dateFrom == null ? null : dateFrom.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = dateTo == null ? null : dateTo.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusNanos(1);
        Page<Order> page = orderService.list(pageable, status, customerName, productId, from, to);
        return PageResponse.from(page, OrderResponse::from);
    }

    @PutMapping("/{id}")
    public OrderResponse update(@PathVariable Long id, @Valid @RequestBody OrderRequest request) {
        return OrderResponse.from(orderService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        orderService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

