package com.ecomtest.service;

import com.ecomtest.dto.OrderRequest;
import com.ecomtest.entity.Order;
import com.ecomtest.entity.OrderStatus;
import com.ecomtest.entity.Product;
import com.ecomtest.entity.User;
import com.ecomtest.entity.UserRole;
import com.ecomtest.exception.ResourceNotFoundException;
import com.ecomtest.repository.OrderRepository;
import com.ecomtest.repository.ProductRepository;
import com.ecomtest.security.CurrentUserService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final CurrentUserService currentUserService;

    public OrderService(OrderRepository orderRepository,
                        ProductRepository productRepository,
                        CurrentUserService currentUserService) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.currentUserService = currentUserService;
    }

    public Order create(OrderRequest request) {
        Order order = new Order();
        applyRequest(order, request);
        order.setCustomer(currentUserService.currentUser());
        order.setCustomerName(currentUserService.username());
        order.setCreatedBy(currentUserService.username());
        return orderRepository.save(order);
    }

    public Order get(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
        User currentUser = currentUserService.currentUser();
        if (currentUser.getRole() == UserRole.CUSTOMER
                && !order.getCustomer().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("Order not found: " + id);
        }
        return order;
    }

    public List<Order> list() {
        User currentUser = currentUserService.currentUser();
        if (currentUser.getRole() == UserRole.CUSTOMER) {
            return orderRepository.findByCustomer(currentUser);
        }
        return orderRepository.findAll();
    }

    public Order update(Long id, OrderRequest request) {
        Order order = get(id);
        OrderStatus previousStatus = order.getStatus();
        applyRequest(order, request);
        if (previousStatus != order.getStatus()) {
            order.setModifiedBy(currentUserService.username());
        }
        return orderRepository.save(order);
    }

    public Order updateStatus(Long id, OrderStatus newStatus) {
        Order order = get(id);
        order.setStatus(newStatus);
        order.setModifiedBy(currentUserService.username());
        return orderRepository.save(order);
    }

    public void delete(Long id) {
        Order order = get(id);
        orderRepository.delete(order);
    }

    private void applyRequest(Order order, OrderRequest request) {
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + request.getProductId()));
        order.setProduct(product);
        order.setQuantity(request.getQuantity());
        order.setUnitPrice(request.getUnitPrice());
        order.setStatus(request.getStatus());
    }
}
