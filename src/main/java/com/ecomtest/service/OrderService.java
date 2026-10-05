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
import com.ecomtest.security.AppUserPrincipal;
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

    public OrderService(OrderRepository orderRepository, ProductRepository productRepository,
                         UserRepository userRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    public Order create(OrderRequest request) {
        Order order = new Order();
        applyRequest(order, request);
        setAuditFields(order, true);
        return orderRepository.save(order);
    }

    public Order get(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
        AppUserPrincipal principal = currentPrincipal();
        if (principal != null && principal.getRole() == Role.CUSTOMER && !isOwnedBy(order, principal)) {
            throw new AccessDeniedException("You do not have permission to access this order");
        }
        return order;
    }

    public List<Order> list() {
        AppUserPrincipal principal = currentPrincipal();
        if (principal != null && principal.getRole() == Role.CUSTOMER) {
            return orderRepository.findAll().stream()
                    .filter(order -> isOwnedBy(order, principal))
                    .toList();
        }
        return orderRepository.findAll();
    }

    public Order update(Long id, OrderRequest request) {
        Order order = get(id);
        AppUserPrincipal principal = currentPrincipal();
        if (principal != null && principal.getRole() == Role.STAFF) {
            order.setStatus(request.getStatus());
        } else {
            applyRequest(order, request);
        }
        setAuditFields(order, false);
        return orderRepository.save(order);
    }

    public void delete(Long id) {
        Order order = get(id);
        orderRepository.delete(order);
    }

    private boolean isOwnedBy(Order order, AppUserPrincipal principal) {
        return order.getCreatedByUser() != null && order.getCreatedByUser().getId().equals(principal.getId());
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

    private void setAuditFields(Order order, boolean isCreate) {
        User currentUser = currentUser();
        if (currentUser == null) {
            return;
        }
        if (isCreate) {
            order.setCreatedByUser(currentUser);
        }
        order.setModifiedByUser(currentUser);
    }

    private User currentUser() {
        AppUserPrincipal principal = currentPrincipal();
        if (principal == null) {
            return null;
        }
        return userRepository.findById(principal.getId()).orElse(null);
    }

    private AppUserPrincipal currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
            return null;
        }
        return principal;
    }
}

