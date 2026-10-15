package com.ecomtest.service;

import com.ecomtest.dto.OrderRequest;
import com.ecomtest.dto.OrderStatusRequest;
import com.ecomtest.entity.Order;
import com.ecomtest.entity.Product;
import com.ecomtest.entity.Role;
import com.ecomtest.entity.User;
import com.ecomtest.exception.ResourceNotFoundException;
import com.ecomtest.repository.OrderRepository;
import com.ecomtest.repository.ProductRepository;
import com.ecomtest.repository.UserRepository;
import com.ecomtest.security.AuthenticatedUser;
import com.ecomtest.security.CurrentUserService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;

    public OrderService(OrderRepository orderRepository, ProductRepository productRepository,
                        UserRepository userRepository, CurrentUserService currentUserService) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
    }

    public Order create(OrderRequest request) {
        Order order = new Order();
        applyRequest(order, request);
        AuthenticatedUser currentUser = currentUserService.currentUser();
        User user = userRepository.findById(currentUser.id())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + currentUser.id()));
        order.setUser(user);
        order.setCreatedBy(currentUserService.currentUsername());
        order.setModifiedBy(currentUserService.currentUsername());
        return orderRepository.save(order);
    }

    public Order get(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
        AuthenticatedUser currentUser = currentUserService.currentUser();
        if (currentUser.role() == Role.CUSTOMER
                && (order.getUser() == null || !order.getUser().getId().equals(currentUser.id()))) {
            throw new AccessDeniedException("Order not found: " + id);
        }
        return order;
    }

    public List<Order> list() {
        AuthenticatedUser currentUser = currentUserService.currentUser();
        if (currentUser.role() == Role.CUSTOMER) {
            return orderRepository.findByUserId(currentUser.id());
        }
        return orderRepository.findAll();
    }

    public Order update(Long id, OrderRequest request) {
        Order order = get(id);
        applyRequest(order, request);
        order.setModifiedBy(currentUserService.currentUsername());
        return orderRepository.save(order);
    }

    public Order updateStatus(Long id, OrderStatusRequest request) {
        Order order = get(id);
        order.setStatus(request.getStatus());
        order.setModifiedBy(currentUserService.currentUsername());
        return orderRepository.save(order);
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
