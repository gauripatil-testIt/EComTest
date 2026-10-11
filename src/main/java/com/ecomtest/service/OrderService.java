package com.ecomtest.service;

import com.ecomtest.dto.OrderRequest;
import com.ecomtest.entity.Order;
import com.ecomtest.entity.Product;
import com.ecomtest.exception.ResourceNotFoundException;
import com.ecomtest.repository.OrderRepository;
import com.ecomtest.repository.ProductRepository;
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

    public OrderService(OrderRepository orderRepository, ProductRepository productRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
    }

    public Order create(OrderRequest request) {
        Order order = new Order();
        applyRequest(order, request);
        return orderRepository.save(order);
    }

    public Order get(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
        if (isCustomer() && !Objects.equals(order.getCreatedBy(), currentUsername())) {
            throw new AccessDeniedException("Customers may only access their own orders");
        }
        return order;
    }

    public List<Order> list() {
        if (isCustomer()) {
            String username = currentUsername();
            return orderRepository.findAll().stream()
                    .filter(order -> Objects.equals(order.getCreatedBy(), username))
                    .toList();
        }
        return orderRepository.findAll();
    }

    public Order update(Long id, OrderRequest request) {
        Order order = get(id);
        if (isStaff()) {
            boolean changesOtherFields =
                    !Objects.equals(order.getCustomerName(), request.getCustomerName())
                            || !Objects.equals(order.getProduct().getId(), request.getProductId())
                            || !Objects.equals(order.getQuantity(), request.getQuantity())
                            || order.getUnitPrice().compareTo(request.getUnitPrice()) != 0;
            if (changesOtherFields) {
                throw new AccessDeniedException("Staff may only update order status");
            }
            order.setStatus(request.getStatus());
            return orderRepository.save(order);
        }
        applyRequest(order, request);
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

    private boolean isCustomer() {
        return hasRole("ROLE_CUSTOMER");
    }

    private boolean isStaff() {
        return hasRole("ROLE_STAFF");
    }

    private boolean hasRole(String role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (role.equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? null : authentication.getName();
    }
}
