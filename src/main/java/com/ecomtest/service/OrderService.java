package com.ecomtest.service;

import com.ecomtest.dto.OrderRequest;
import com.ecomtest.entity.Order;
import com.ecomtest.entity.Product;
import com.ecomtest.entity.Role;
import com.ecomtest.entity.User;
import com.ecomtest.exception.ResourceNotFoundException;
import com.ecomtest.repository.OrderRepository;
import com.ecomtest.repository.ProductRepository;
import com.ecomtest.repository.UserRepository;
import com.ecomtest.security.AuthenticatedUserProvider;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final AuthenticatedUserProvider authenticatedUserProvider;

    public OrderService(OrderRepository orderRepository, ProductRepository productRepository,
                         UserRepository userRepository,
                         AuthenticatedUserProvider authenticatedUserProvider) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.authenticatedUserProvider = authenticatedUserProvider;
    }

    public Order create(OrderRequest request) {
        Order order = new Order();
        if (isCustomer()) {
            request.setCustomerName(authenticatedUserProvider.currentUsername());
        }
        applyRequest(order, request);
        User currentUser = currentUser();
        order.setCreatedBy(currentUser);
        order.setModifiedBy(currentUser);
        return orderRepository.save(order);
    }

    public Order get(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
        if (isCustomer() && !order.getCustomerName().equals(authenticatedUserProvider.currentUsername())) {
            throw new ResourceNotFoundException("Order not found: " + id);
        }
        return order;
    }

    public List<Order> list() {
        List<Order> orders = orderRepository.findAll();
        if (isCustomer()) {
            String username = authenticatedUserProvider.currentUsername();
            return orders.stream()
                    .filter(order -> order.getCustomerName().equals(username))
                    .toList();
        }
        return orders;
    }

    public Order update(Long id, OrderRequest request) {
        Order order = get(id);
        if (isStaff()) {
            order.setStatus(request.getStatus());
        } else {
            applyRequest(order, request);
        }
        order.setModifiedBy(currentUser());
        return orderRepository.save(order);
    }

    public void delete(Long id) {
        Order order = get(id);
        orderRepository.delete(order);
    }

    private boolean isCustomer() {
        return authenticatedUserProvider.currentRole() == Role.CUSTOMER;
    }

    private boolean isStaff() {
        return authenticatedUserProvider.currentRole() == Role.STAFF;
    }

    private User currentUser() {
        String username = authenticatedUserProvider.currentUsername();
        if (username == null) {
            return null;
        }
        return userRepository.findByUsername(username).orElse(null);
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
