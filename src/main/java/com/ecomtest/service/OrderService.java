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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
        Order order = new Order();
        applyRequest(order, request);
        return orderRepository.save(order);
    }

    public Order get(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
        User currentUser = currentUser();
        if (currentUser != null && currentUser.getRole() == Role.CUSTOMER
                && (order.getCustomerUser() == null || !order.getCustomerUser().getId().equals(currentUser.getId()))) {
            throw new ResourceNotFoundException("Order not found: " + id);
        }
        return order;
    }

    public List<Order> list() {
        List<Order> orders = orderRepository.findAll();
        User currentUser = currentUser();
        if (currentUser != null && currentUser.getRole() == Role.CUSTOMER) {
            return orders.stream()
                    .filter(order -> order.getCustomerUser() != null
                            && order.getCustomerUser().getId().equals(currentUser.getId()))
                    .toList();
        }
        return orders;
    }

    public Order update(Long id, OrderRequest request) {
        Order order = get(id);
        User currentUser = currentUser();
        if (currentUser != null && currentUser.getRole() == Role.STAFF) {
            boolean changesNonStatusFields =
                    !java.util.Objects.equals(order.getCustomerName(), request.getCustomerName())
                            || !java.util.Objects.equals(order.getProduct().getId(), request.getProductId())
                            || !java.util.Objects.equals(order.getQuantity(), request.getQuantity())
                            || (order.getUnitPrice() != null && request.getUnitPrice() != null
                                    && order.getUnitPrice().compareTo(request.getUnitPrice()) != 0);
            if (changesNonStatusFields) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.FORBIDDEN,
                        "STAFF may only update order status");
            }
        }
        applyRequest(order, request);
        return orderRepository.save(order);
    }

    public void delete(Long id) {
        Order order = get(id);
        orderRepository.delete(order);
    }

    private void applyRequest(Order order, OrderRequest request) {
        boolean isNew = order.getId() == null;
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + request.getProductId()));
        order.setCustomerName(request.getCustomerName());
        order.setProduct(product);
        order.setQuantity(request.getQuantity());
        order.setUnitPrice(request.getUnitPrice());
        order.setStatus(request.getStatus());

        User currentUser = currentUser();
        if (isNew) {
            order.setCreatedBy(currentUser);
            if (currentUser != null && currentUser.getRole() == Role.CUSTOMER) {
                order.setCustomerUser(currentUser);
            }
        }
        order.setModifiedBy(currentUser);
    }

    private User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        return userRepository.findByEmail(authentication.getName()).orElse(null);
    }
}



