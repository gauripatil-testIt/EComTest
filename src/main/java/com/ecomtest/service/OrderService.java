package com.ecomtest.service;

import com.ecomtest.dto.OrderRequest;
import com.ecomtest.entity.Order;
import com.ecomtest.entity.Product;
import com.ecomtest.exception.ResourceNotFoundException;
import com.ecomtest.repository.OrderRepository;
import com.ecomtest.repository.ProductRepository;
import com.ecomtest.repository.UserRepository;
import com.ecomtest.entity.User;
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
        applyRequest(order, request);
        return orderRepository.save(order);
    }

    public Order get(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));

        String username = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
        User actingUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        if (actingUser.getRole() == com.ecomtest.entity.Role.CUSTOMER && order.getCreatedBy() != null && !order.getCreatedBy().getId().equals(actingUser.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied");
        }

        return order;
    }

    public List<Order> list() {
        String username = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
        User actingUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        if (actingUser.getRole() == com.ecomtest.entity.Role.CUSTOMER) {
            return orderRepository.findAllByCreatedBy(actingUser);
        }

        return orderRepository.findAll();
    }

    public Order update(Long id, OrderRequest request) {
        Order order = get(id);

        String username = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
        User actingUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        if (actingUser.getRole() == com.ecomtest.entity.Role.STAFF) {
            order.setStatus(request.getStatus());
        } else {
            applyRequest(order, request);
        }

        order.setModifiedBy(actingUser);
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

        String username = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
        User actingUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        if (order.getId() == null) {
            order.setCreatedBy(actingUser);
        }
        order.setModifiedBy(actingUser);
    }
}
