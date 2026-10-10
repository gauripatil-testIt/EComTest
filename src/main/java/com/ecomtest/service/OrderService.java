package com.ecomtest.service;

import com.ecomtest.dto.OrderRequest;
import com.ecomtest.entity.Order;
import com.ecomtest.entity.Product;
import com.ecomtest.entity.User;
import com.ecomtest.exception.ResourceNotFoundException;
import com.ecomtest.repository.OrderRepository;
import com.ecomtest.repository.ProductRepository;
import com.ecomtest.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public OrderService(OrderRepository orderRepository, ProductRepository productRepository, UserRepository userRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    public Order create(OrderRequest request) {
        Order order = new Order();
        applyRequest(order, request);
        if (isCustomer() || request.getCustomerId() == null) {
            order.setCustomerId(currentUserId());
        } else {
            order.setCustomerId(request.getCustomerId());
        }
        String username = currentUsername();
        Instant now = Instant.now();
        order.setCreatedBy(username);
        order.setCreatedAt(now);
        order.setLastStatusChangedBy(username);
        return orderRepository.save(order);
    }

    public Order get(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
        if (isCustomer() && !Objects.equals(order.getCustomerId(), currentUserId())) {
            throw new AccessDeniedException("Customers may only view their own orders");
        }
        return order;
    }

    public List<Order> list() {
        List<Order> orders = orderRepository.findAll();
        if (isCustomer()) {
            Long currentUserId = currentUserId();
            return orders.stream()
                    .filter(order -> Objects.equals(order.getCustomerId(), currentUserId))
                    .toList();
        }
        return orders;
    }

    public Order update(Long id, OrderRequest request) {
        Order order = get(id);
        if (isStaff() && !isStatusOnlyChange(order, request)) {
            throw new AccessDeniedException("Staff may only update an order's status");
        }
        boolean statusChanged = order.getStatus() != request.getStatus();
        applyRequest(order, request);
        String username = currentUsername();
        order.setModifiedBy(username);
        order.setModifiedAt(Instant.now());
        if (statusChanged) {
            order.setLastStatusChangedBy(username);
        }
        return orderRepository.save(order);
    }

    public void delete(Long id) {
        Order order = get(id);
        orderRepository.delete(order);
    }

    private boolean isStatusOnlyChange(Order order, OrderRequest request) {
        return Objects.equals(order.getCustomerName(), request.getCustomerName())
                && Objects.equals(order.getProduct().getId(), request.getProductId())
                && Objects.equals(order.getQuantity(), request.getQuantity())
                && order.getUnitPrice().compareTo(request.getUnitPrice()) == 0;
    }

    private boolean isCustomer() {
        return hasRole("ROLE_CUSTOMER");
    }

    private boolean isStaff() {
        return hasRole("ROLE_STAFF");
    }

    private boolean hasRole(String authority) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(granted -> granted.getAuthority().equals(authority));
    }

    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        return userRepository.findByUsername(authentication.getName())
                .map(User::getId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + authentication.getName()));
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? null : authentication.getName();
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


