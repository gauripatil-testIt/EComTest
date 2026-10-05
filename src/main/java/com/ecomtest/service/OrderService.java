package com.ecomtest.service;

import com.ecomtest.dto.OrderRequest;
import com.ecomtest.entity.Order;
import com.ecomtest.entity.OrderStatus;
import com.ecomtest.entity.Product;
import com.ecomtest.entity.Role;
import com.ecomtest.entity.User;
import com.ecomtest.exception.ResourceNotFoundException;
import com.ecomtest.repository.OrderRepository;
import com.ecomtest.repository.ProductRepository;
import com.ecomtest.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
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
        applyRequest(order, request);
        return orderRepository.save(order);
    }

    public Order get(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
        User currentUser = currentUser();
        if (currentUser != null && currentUser.getRoles().contains(Role.CUSTOMER)
                && (order.getCreatedBy() == null || !order.getCreatedBy().getId().equals(currentUser.getId()))) {
            throw new AccessDeniedException("Not allowed to access this order");
        }
        return order;
    }

    public List<Order> list() {
        User currentUser = currentUser();
        if (currentUser != null && currentUser.getRoles().contains(Role.CUSTOMER)) {
            return orderRepository.findAll().stream()
                    .filter(order -> order.getCreatedBy() != null && order.getCreatedBy().getId().equals(currentUser.getId()))
                    .toList();
        }
        return orderRepository.findAll();
    }

    public Order update(Long id, OrderRequest request) {
        Order order = get(id);
        applyRequest(order, request);
        return orderRepository.save(order);
    }

    public Order updateStatus(Long id, OrderStatus status) {
        Order order = get(id);
        order.setStatus(status);
        User currentUser = currentUser();
        if (currentUser != null) {
            order.setModifiedBy(currentUser);
        }
        return orderRepository.save(order);
    }

    public void delete(Long id) {
        Order order = get(id);
        orderRepository.delete(order);
    }

    public String exportCsv() {
        StringBuilder csv = new StringBuilder();
        csv.append("id,customerName,productId,quantity,unitPrice,status,createdByUserId,modifiedByUserId\n");
        for (Order order : orderRepository.findAll()) {
            Long createdByUserId = order.getCreatedBy() != null ? order.getCreatedBy().getId() : null;
            Long modifiedByUserId = order.getModifiedBy() != null ? order.getModifiedBy().getId() : null;
            csv.append(order.getId()).append(',')
                    .append(order.getCustomerName()).append(',')
                    .append(order.getProduct().getId()).append(',')
                    .append(order.getQuantity()).append(',')
                    .append(order.getUnitPrice()).append(',')
                    .append(order.getStatus()).append(',')
                    .append(createdByUserId == null ? "" : createdByUserId).append(',')
                    .append(modifiedByUserId == null ? "" : modifiedByUserId)
                    .append('\n');
        }
        return csv.toString();
    }

    private void applyRequest(Order order, OrderRequest request) {
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + request.getProductId()));
        order.setCustomerName(request.getCustomerName());
        order.setProduct(product);
        order.setQuantity(request.getQuantity());
        order.setUnitPrice(request.getUnitPrice());
        order.setStatus(request.getStatus());

        User currentUser = currentUser();
        if (order.getId() == null) {
            if (currentUser != null) {
                order.setCreatedBy(currentUser);
            }
        } else if (currentUser != null) {
            order.setModifiedBy(currentUser);
        }
    }

    private User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        return userRepository.findByUsername(authentication.getName()).orElse(null);
    }
}
