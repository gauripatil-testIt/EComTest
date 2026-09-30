package com.ecomtest.service;

import com.ecomtest.dto.OrderRequest;
import com.ecomtest.entity.Order;
import com.ecomtest.entity.OrderStatus;
import com.ecomtest.entity.Product;
import com.ecomtest.event.OrderStatusChangedEvent;
import com.ecomtest.exception.ResourceNotFoundException;
import com.ecomtest.repository.OrderRepository;
import com.ecomtest.repository.ProductRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final ApplicationEventPublisher eventPublisher;

    public OrderService(OrderRepository orderRepository, ProductRepository productRepository,
                         ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Order create(OrderRequest request) {
        Order order = new Order();
        applyRequest(order, request);
        Order saved = orderRepository.save(order);
        eventPublisher.publishEvent(toEvent(saved, null, saved.getStatus()));
        return saved;
    }

    public Order get(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
    }

    public List<Order> list() {
        return orderRepository.findAll();
    }

    @Transactional
    public Order update(Long id, OrderRequest request) {
        Order order = get(id);
        OrderStatus previousStatus = order.getStatus();
        applyRequest(order, request);
        Order saved = orderRepository.save(order);
        if (previousStatus != saved.getStatus() && saved.getStatus() != OrderStatus.PENDING) {
            eventPublisher.publishEvent(toEvent(saved, previousStatus, saved.getStatus()));
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
        order.setCustomerEmail(request.getCustomerEmail());
        order.setTrackingNumber(request.getTrackingNumber());
    }

    private OrderStatusChangedEvent toEvent(Order order, OrderStatus previousStatus, OrderStatus newStatus) {
        return new OrderStatusChangedEvent(
                order.getId(),
                previousStatus,
                newStatus,
                order.getCustomerEmail(),
                order.getCustomerName(),
                order.getProduct().getName(),
                order.getQuantity(),
                order.getUnitPrice(),
                order.getTrackingNumber());
    }
}
