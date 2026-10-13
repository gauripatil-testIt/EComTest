package com.ecomtest.service;

import com.ecomtest.dto.OrderRequest;
import com.ecomtest.entity.Order;
import com.ecomtest.entity.Product;
import com.ecomtest.entity.UserRole;
import com.ecomtest.exception.ResourceNotFoundException;
import com.ecomtest.repository.OrderRepository;
import com.ecomtest.repository.ProductRepository;
import com.ecomtest.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public OrderService(OrderRepository orderRepository, ProductRepository productRepository,
                         UserRepository userRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    public Order create(OrderRequest request) {
        Order order = new Order();
        applyRequest(order, request);
        String currentUsername = currentUsername();
        order.setCreatedBy(currentUsername);
        order.setModifiedBy(currentUsername);
        if (currentUsername != null) {
            userRepository.findByUsername(currentUsername).ifPresent(order::setCreatedByUser);
        }
        return orderRepository.save(order);
    }

    public Order get(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
        if (isCustomer() && !isOwner(order)) {
            throw new ResourceNotFoundException("Order not found: " + id);
        }
        return order;
    }

    public List<Order> list() {
        List<Order> orders = orderRepository.findAll();
        if (isCustomer()) {
            return orders.stream().filter(this::isOwner).toList();
        }
        return orders;
    }

    public Order update(Long id, OrderRequest request) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));

        if (isStaff()) {
            Product requestedProduct = productRepository.findById(request.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + request.getProductId()));
            boolean onlyStatusChanged = Objects.equals(order.getCustomerName(), request.getCustomerName())
                    && Objects.equals(order.getProduct().getId(), requestedProduct.getId())
                    && Objects.equals(order.getQuantity(), request.getQuantity())
                    && order.getUnitPrice() != null && request.getUnitPrice() != null
                    && order.getUnitPrice().compareTo(request.getUnitPrice()) == 0;
            if (!onlyStatusChanged) {
                throw new AccessDeniedException("STAFF may only update order status");
            }
            order.setStatus(request.getStatus());
            order.setModifiedBy(currentUsername());
            return orderRepository.save(order);
        }

        applyRequest(order, request);
        order.setModifiedBy(currentUsername());
        return orderRepository.save(order);
    }

    public void delete(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
        orderRepository.delete(order);
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        return principal != null ? principal.toString() : null;
    }

    private UserRole currentRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String name = authority.getAuthority();
            if (name != null && name.startsWith("ROLE_")) {
                try {
                    return UserRole.valueOf(name.substring("ROLE_".length()));
                } catch (IllegalArgumentException ex) {
                    return null;
                }
            }
        }
        return null;
    }

    private boolean isCustomer() {
        return currentRole() == UserRole.CUSTOMER;
    }

    private boolean isStaff() {
        return currentRole() == UserRole.STAFF;
    }

    private boolean isOwner(Order order) {
        String username = currentUsername();
        return username != null && order.getCreatedByUser() != null
                && username.equals(order.getCreatedByUser().getUsername());
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

