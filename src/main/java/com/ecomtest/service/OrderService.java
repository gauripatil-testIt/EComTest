package com.ecomtest.service;

import com.ecomtest.dto.OrderRequest;
import com.ecomtest.dto.OrderStatusUpdateRequest;
import com.ecomtest.entity.Order;
import com.ecomtest.entity.Product;
import com.ecomtest.entity.Role;
import com.ecomtest.entity.User;
import com.ecomtest.exception.ResourceNotFoundException;
import com.ecomtest.repository.OrderRepository;
import com.ecomtest.repository.ProductRepository;
import com.ecomtest.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

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
        applyRequest(order, request, true);
        return orderRepository.save(order);
    }

    public Order get(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
        if (isCustomer()) {
            User currentUser = currentUser();
            if (order.getOwner() == null || !order.getOwner().getId().equals(currentUser.getId())) {
                throw new ResourceNotFoundException("Order not found: " + id);
            }
        }
        return order;
    }

    public List<Order> list() {
        List<Order> orders = orderRepository.findAll();
        if (isCustomer()) {
            User currentUser = currentUser();
            return orders.stream()
                    .filter(order -> order.getOwner() != null && order.getOwner().getId().equals(currentUser.getId()))
                    .toList();
        }
        return orders;
    }

    public Order update(Long id, OrderRequest request) {
        Order order = get(id);
        applyRequest(order, request, false);
        return orderRepository.save(order);
    }

    public Order updateStatus(Long id, OrderStatusUpdateRequest request) {
        Order order = get(id);
        order.setStatus(request.getStatus());
        order.setModifiedBy(currentUser());
        return orderRepository.save(order);
    }

    public void delete(Long id) {
        Order order = get(id);
        orderRepository.delete(order);
    }

    private void applyRequest(Order order, OrderRequest request, boolean isCreate) {
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + request.getProductId()));
        order.setCustomerName(request.getCustomerName());
        order.setProduct(product);
        order.setQuantity(request.getQuantity());
        order.setUnitPrice(request.getUnitPrice());
        order.setStatus(request.getStatus());

        User currentUser = currentUser();
        if (isCreate) {
            order.setOwner(resolveOwner(request, currentUser));
            order.setCreatedBy(currentUser);
        }
        order.setModifiedBy(currentUser);
    }

    private User resolveOwner(OrderRequest request, User currentUser) {
        if (currentUser.getRole() == Role.ADMIN && request.getUserId() != null) {
            return userRepository.findById(request.getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + request.getUserId()));
        }
        return currentUser;
    }

    private boolean isCustomer() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_CUSTOMER"::equals);
    }

    private User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
    }
}
