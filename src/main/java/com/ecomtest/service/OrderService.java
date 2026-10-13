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
import com.ecomtest.security.SecurityUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;

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
        User currentUser = SecurityUtils.currentUser();
        Order order = new Order();
        applyRequest(order, request, currentUser);
        order.setCreatedBy(currentUser);
        order.setModifiedBy(currentUser);
        if (currentUser.getRole() == Role.CUSTOMER) {
            order.setCustomerUser(currentUser);
        }
        return orderRepository.save(order);
    }

    public Order get(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
        User currentUser = SecurityUtils.currentUser();
        if (currentUser.getRole() == Role.CUSTOMER
                && (order.getCustomerUser() == null || !order.getCustomerUser().getId().equals(currentUser.getId()))) {
            throw new AccessDeniedException("Not allowed to access this order");
        }
        return order;
    }

    public List<Order> list() {
        User currentUser = SecurityUtils.currentUser();
        if (currentUser.getRole() == Role.CUSTOMER) {
            return orderRepository.findByCustomerUser(currentUser);
        }
        return orderRepository.findAll();
    }

    public Order update(Long id, OrderRequest request) {
        Order order = get(id);
        User currentUser = SecurityUtils.currentUser();
        if (currentUser.getRole() == Role.STAFF) {
            order.setStatus(request.getStatus());
        } else {
            applyRequest(order, request, currentUser);
        }
        order.setModifiedBy(currentUser);
        return orderRepository.save(order);
    }

    public void delete(Long id) {
        Order order = get(id);
        orderRepository.delete(order);
    }

    private void applyRequest(Order order, OrderRequest request, User currentUser) {
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + request.getProductId()));
        order.setCustomerName(request.getCustomerName());
        order.setProduct(product);
        order.setQuantity(request.getQuantity());
        order.setUnitPrice(request.getUnitPrice());
        order.setStatus(request.getStatus());

        if (currentUser.getRole() != Role.CUSTOMER && request.getCustomerUserId() != null) {
            // Only ADMIN/STAFF may assign orders to a specific customer; CUSTOMER role ignores this field
            // and is forced onto their own user in create().
            User customerUser = userRepository.findById(request.getCustomerUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + request.getCustomerUserId()));
            order.setCustomerUser(customerUser);
        }
    }
}
