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
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    public OrderService(OrderRepository orderRepository, ProductRepository productRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
    }

    @Transactional
    public Order create(OrderRequest request) {
        Product product = findProduct(request.getProductId());
        if (request.getStatus() != OrderStatus.CANCELLED) {
            deductStock(product, request.getQuantity());
        }
        Order order = new Order();
        applyRequest(order, request, product);
        return orderRepository.save(order);
    }

    public Order get(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
    }

    public List<Order> list() {
        return orderRepository.findAll();
    }

    @Transactional
    public Order update(Long id, OrderRequest request) {
        Order order = get(id);
        Product newProduct = findProduct(request.getProductId());
        if (order.getStatus() != OrderStatus.CANCELLED) {
            restoreStock(order.getProduct(), order.getQuantity());
        }
        if (request.getStatus() != OrderStatus.CANCELLED) {
            deductStock(newProduct, request.getQuantity());
        }
        applyRequest(order, request, newProduct);
        return orderRepository.save(order);
    }

    @Transactional
    public void delete(Long id) {
        Order order = get(id);
        if (order.getStatus() != OrderStatus.CANCELLED) {
            restoreStock(order.getProduct(), order.getQuantity());
        }
        orderRepository.delete(order);
    }

    private void deductStock(Product product, Integer quantity) {
        if (product.getStock() < quantity) {
            throw new InsufficientStockException(quantity, product.getStock());
        }
        product.setStock(product.getStock() - quantity);
    }

    private void restoreStock(Product product, Integer quantity) {
        product.setStock(product.getStock() + quantity);
    }

    private Product findProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
    }

    private void applyRequest(Order order, OrderRequest request, Product product) {
        order.setCustomerName(request.getCustomerName());
        order.setProduct(product);
        order.setQuantity(request.getQuantity());
        order.setUnitPrice(request.getUnitPrice());
        order.setStatus(request.getStatus());
    }
}
