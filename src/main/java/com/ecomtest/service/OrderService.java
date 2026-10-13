package com.ecomtest.service;

import com.ecomtest.dto.OrderRequest;
import com.ecomtest.entity.Order;
import com.ecomtest.entity.OrderEmail;
import com.ecomtest.entity.OrderEmailState;
import com.ecomtest.entity.OrderStatus;
import com.ecomtest.entity.Product;
import com.ecomtest.event.OrderEmailRequested;
import com.ecomtest.exception.ResourceNotFoundException;
import com.ecomtest.repository.OrderEmailRepository;
import com.ecomtest.repository.OrderRepository;
import com.ecomtest.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final OrderEmailRepository orderEmailRepository;
    private final ApplicationEventPublisher eventPublisher;

    public OrderService(OrderRepository orderRepository, ProductRepository productRepository,
            OrderEmailRepository orderEmailRepository, ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.orderEmailRepository = orderEmailRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Order create(OrderRequest request) {
        Order order = new Order();
        applyRequest(order, request);
        Order saved = orderRepository.save(order);
        recordEmailIfNeeded(saved, null);
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
        recordEmailIfNeeded(saved, previousStatus);
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
        order.setTrackingUrl(request.getTrackingUrl());
    }

    private void recordEmailIfNeeded(Order order, OrderStatus previousStatus) {
        if (order.getCustomerEmail() == null || order.getCustomerEmail().isBlank()) {
            log.info("Order {} has no customerEmail; skipping email notification", order.getId());
            return;
        }
        OrderStatus currentStatus = order.getStatus();
        if (previousStatus != null && previousStatus == currentStatus) {
            return;
        }
        OrderEmail orderEmail = new OrderEmail();
        orderEmail.setOrder(order);
        orderEmail.setStatus(currentStatus);
        orderEmail.setAttempts(0);
        orderEmail.setState(OrderEmailState.PENDING);
        try {
            OrderEmail saved = orderEmailRepository.save(orderEmail);
            eventPublisher.publishEvent(new OrderEmailRequested(saved.getId()));
        } catch (DataIntegrityViolationException ex) {
            log.info("Email already recorded for order {} status {}; skipping duplicate", order.getId(), currentStatus);
        }
    }
}
