package com.ecomtest.service;

import com.ecomtest.dto.OrderRequest;
import com.ecomtest.entity.Order;
import com.ecomtest.entity.OrderStatus;
import com.ecomtest.entity.Product;
import com.ecomtest.exception.InsufficientStockException;
import com.ecomtest.exception.ResourceNotFoundException;
import com.ecomtest.repository.OrderRepository;
import com.ecomtest.repository.ProductRepository;
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
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + request.getProductId()));

        int held = request.getStatus() == OrderStatus.CANCELLED ? 0 : request.getQuantity();
        if (held > product.getStock()) {
            throw new InsufficientStockException(
                    "Insufficient stock for product: " + product.getId(),
                    request.getQuantity(),
                    product.getStock());
        }

        Order order = new Order();
        order.setCustomerName(request.getCustomerName());
        order.setProduct(product);
        order.setQuantity(request.getQuantity());
        order.setUnitPrice(request.getUnitPrice());
        order.setStatus(request.getStatus());

        if (held > 0) {
            product.setStock(product.getStock() - held);
            productRepository.save(product);
        }

        return orderRepository.save(order);
    }

    public Order get(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
    }

    public List<Order> list() {
        return orderRepository.findAll();
    }

    public Order update(Long id, OrderRequest request) {
        Order order = get(id);
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + request.getProductId()));

        int oldHeld = order.getStatus() == OrderStatus.CANCELLED ? 0 : order.getQuantity();
        int newHeld = request.getStatus() == OrderStatus.CANCELLED ? 0 : request.getQuantity();
        int availableForThisOrder = product.getStock() + oldHeld;

        if (newHeld > availableForThisOrder) {
            throw new InsufficientStockException(
                    "Insufficient stock for product: " + product.getId(),
                    request.getQuantity(),
                    availableForThisOrder);
        }

        order.setCustomerName(request.getCustomerName());
        order.setProduct(product);
        order.setQuantity(request.getQuantity());
        order.setUnitPrice(request.getUnitPrice());
        order.setStatus(request.getStatus());

        product.setStock(availableForThisOrder - newHeld);
        productRepository.save(product);

        return orderRepository.save(order);
    }

    public void delete(Long id) {
        Order order = get(id);
        if (order.getStatus() != OrderStatus.CANCELLED) {
            Product product = order.getProduct();
            product.setStock(product.getStock() + order.getQuantity());
            productRepository.save(product);
        }
        orderRepository.delete(order);
    }
}
