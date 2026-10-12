package com.ecomtest.service;

import com.ecomtest.dto.OrderRequest;
import com.ecomtest.dto.OrderStatusRequest;
import com.ecomtest.entity.Order;
import com.ecomtest.entity.Product;
import com.ecomtest.exception.ResourceNotFoundException;
import com.ecomtest.repository.OrderRepository;
import com.ecomtest.repository.ProductRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

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
        if (isCustomer()) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();
            if (!username.equals(order.getCustomerUsername())) {
                throw new AccessDeniedException("Not permitted to access this order");
            }
        }
        return order;
    }

    public List<Order> list() {
        List<Order> orders = orderRepository.findAll();
        if (isCustomer()) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();
            return orders.stream()
                    .filter(order -> username.equals(order.getCustomerUsername()))
                    .toList();
        }
        return orders;
    }

    public Order update(Long id, OrderRequest request) {
        Order order = get(id);
        applyRequest(order, request);
        return orderRepository.save(order);
    }

    public Order updateStatus(Long id, OrderStatusRequest request) {
        Order order = get(id);
        order.setStatus(request.getStatus());
        return orderRepository.save(order);
    }

    public void delete(Long id) {
        Order order = get(id);
        orderRepository.delete(order);
    }

    private void applyRequest(Order order, OrderRequest request) {
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + request.getProductId()));
        boolean isNew = order.getId() == null;
        order.setCustomerName(request.getCustomerName());
        order.setProduct(product);
        order.setQuantity(request.getQuantity());
        order.setUnitPrice(request.getUnitPrice());
        order.setStatus(request.getStatus());

        if (isNew) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()
                    && !"anonymousUser".equals(authentication.getPrincipal())
                    && authentication.getAuthorities().stream()
                            .anyMatch(authority -> authority.getAuthority().equals("ROLE_CUSTOMER"))) {
                order.setCustomerUsername(authentication.getName());
            }
        }
    }

    private boolean isCustomer() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_CUSTOMER"));
    }
}
