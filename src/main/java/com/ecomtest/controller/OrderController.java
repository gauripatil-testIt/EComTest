package com.ecomtest.controller;

import com.ecomtest.dto.OrderRequest;
import com.ecomtest.dto.OrderResponse;
import com.ecomtest.dto.PageResponse;
import com.ecomtest.entity.Order;
import com.ecomtest.entity.OrderStatus;
import com.ecomtest.service.OrderService;
import com.ecomtest.util.PageableUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
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

@RestController
@RequestMapping("/api/orders")
@Validated
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
    public PageResponse<OrderResponse> list(
            @RequestParam(required = false) @Min(0) Integer page,
            @RequestParam(required = false) @Min(1) @Max(100) Integer size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Instant dateFrom,
            @RequestParam(required = false) Instant dateTo) {
        Pageable pageable = PageableUtils.toPageable(page, size, sort, "id");
        Page<Order> orders = orderService.list(status, customerName, productId, dateFrom, dateTo, pageable);
        return PageResponse.from(orders.map(OrderResponse::from));
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
