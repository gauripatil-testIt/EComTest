package com.ecomtest.service;

import com.ecomtest.dto.OrderRequest;
import com.ecomtest.entity.Order;
import com.ecomtest.entity.OrderStatus;
import com.ecomtest.entity.Product;
import com.ecomtest.exception.ResourceNotFoundException;
import com.ecomtest.repository.OrderRepository;
import com.ecomtest.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final OrderEmailDispatcher orderEmailDispatcher;

    public OrderService(OrderRepository orderRepository, ProductRepository productRepository,
                         OrderEmailDispatcher orderEmailDispatcher) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.orderEmailDispatcher = orderEmailDispatcher;
    }

    public Order create(OrderRequest request) {
        Order order = new Order();
        applyRequest(order, request);
        Order saved = orderRepository.save(order);
        orderEmailDispatcher.dispatch(saved, saved.getStatus());
        return saved;
    }

    public Order get(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
    }

    public List<Order> list() {
        return orderRepository.findAll();
    }

    public Order update(Long id, OrderRequest request) {
        Order order = get(id);
        OrderStatus previousStatus = order.getStatus();
        applyRequest(order, request);
        Order saved = orderRepository.save(order);
        if (saved.getStatus() != previousStatus) {
            orderEmailDispatcher.dispatch(saved, saved.getStatus());
        }
        return saved;
    }

    public void delete(Long id) {
        Order order = get(id);
        orderRepository.delete(order);
    }

    private void applyRequest(Order order, OrderRequest request) {
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + request.getProductId()));
        order.setCustomerName(request.getCustomerName());
        order.setProduct(product);
        order.setQuantity(request.getQuantity());
        order.setUnitPrice(request.getUnitPrice());
        order.setStatus(request.getStatus());
    }
}
