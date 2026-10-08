package com.ecomtest.service;

import com.ecomtest.dto.OrderRequest;
import com.ecomtest.entity.Order;
import com.ecomtest.entity.Product;
import com.ecomtest.entity.Role;
import com.ecomtest.entity.User;
import com.ecomtest.exception.ResourceNotFoundException;
import com.ecomtest.repository.OrderRepository;
import com.ecomtest.repository.ProductRepository;
import com.ecomtest.security.CurrentUserProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final CurrentUserProvider currentUserProvider;

    public OrderService(OrderRepository orderRepository, ProductRepository productRepository,
                         CurrentUserProvider currentUserProvider) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.currentUserProvider = currentUserProvider;
    }

    public Order create(OrderRequest request) {
        Order order = new Order();
        applyRequest(order, request);
        return orderRepository.save(order);
    }

    public Order get(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
        User currentUser = currentUserProvider.getCurrentUser();
        if (currentUser.getRole() == Role.CUSTOMER
                && (order.getCustomer() == null || !order.getCustomer().getId().equals(currentUser.getId()))) {
            throw new AccessDeniedException("Not allowed to access this order");
        }
        return order;
    }

    public List<Order> list() {
        User currentUser = currentUserProvider.getCurrentUser();
        if (currentUser.getRole() == Role.CUSTOMER) {
            return orderRepository.findByCustomer(currentUser);
        }
        return orderRepository.findAll();
    }

    public Order update(Long id, OrderRequest request) {
        Order order = get(id);
        applyRequest(order, request);
        return orderRepository.save(order);
    }

    public Order updateStatus(Long id, com.ecomtest.entity.OrderStatus status) {
        Order order = get(id);
        order.setStatus(status);
        order.setModifiedBy(currentUserProvider.getCurrentUser());
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

        User currentUser = currentUserProvider.getCurrentUser();
        if (order.getId() == null) {
            order.setCreatedBy(currentUser);
            order.setCustomer(currentUser);
        }
        order.setModifiedBy(currentUser);
    }
}
