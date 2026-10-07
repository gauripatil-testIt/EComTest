package com.ecomtest.service;

import com.ecomtest.dto.OrderRequest;
import com.ecomtest.entity.Order;
import com.ecomtest.entity.Product;
import com.ecomtest.entity.Role;
import com.ecomtest.entity.User;
import com.ecomtest.exception.ResourceNotFoundException;
import com.ecomtest.repository.OrderRepository;
import com.ecomtest.repository.ProductRepository;
import com.ecomtest.security.CurrentUserService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final CurrentUserService currentUserService;

    public OrderService(OrderRepository orderRepository, ProductRepository productRepository,
                         CurrentUserService currentUserService) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.currentUserService = currentUserService;
    }

    public Order create(OrderRequest request) {
        Order order = new Order();
        applyRequest(order, request, true);
        return orderRepository.save(order);
    }

    public Order get(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
        User currentUser = currentUserService.getCurrentUser();
        if (currentUser.getRole() == Role.CUSTOMER
                && (order.getUser() == null || !order.getUser().getId().equals(currentUser.getId()))) {
            throw new AccessDeniedException("Not allowed to access this order");
        }
        return order;
    }

    public List<Order> list() {
        User currentUser = currentUserService.getCurrentUser();
        if (currentUser.getRole() == Role.CUSTOMER) {
            return orderRepository.findByUser(currentUser);
        }
        return orderRepository.findAll();
    }

    public Order update(Long id, OrderRequest request) {
        Order order = get(id);
        User currentUser = currentUserService.getCurrentUser();
        if (currentUser.getRole() == Role.STAFF) {
            order.setStatus(request.getStatus());
            order.setModifiedBy(currentUser);
        } else {
            applyRequest(order, request, false);
        }
        return orderRepository.save(order);
    }

    public void delete(Long id) {
        Order order = get(id);
        orderRepository.delete(order);
    }

    private void applyRequest(Order order, OrderRequest request, boolean isNew) {
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + request.getProductId()));
        order.setCustomerName(request.getCustomerName());
        order.setProduct(product);
        order.setQuantity(request.getQuantity());
        order.setUnitPrice(request.getUnitPrice());
        order.setStatus(request.getStatus());

        User currentUser = currentUserService.getCurrentUser();
        if (isNew) {
            order.setUser(currentUser);
            order.setCreatedBy(currentUser);
        }
        order.setModifiedBy(currentUser);
    }
}
