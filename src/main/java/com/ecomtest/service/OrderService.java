package com.ecomtest.service;

import com.ecomtest.dto.OrderRequest;
import com.ecomtest.entity.Order;
import com.ecomtest.entity.OrderStatus;
import com.ecomtest.entity.Product;
import com.ecomtest.exception.InsufficientStockException;
import com.ecomtest.exception.ResourceNotFoundException;
import com.ecomtest.repository.OrderRepository;
import com.ecomtest.repository.ProductRepository;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Supplier;

@Service
public class OrderService {

    private static final int MAX_RETRY_ATTEMPTS = 3;

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    public OrderService(OrderRepository orderRepository, ProductRepository productRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
    }

    @Transactional
    public Order create(OrderRequest request) {
        return executeWithRetry(() -> {
            Product product = productRepository.findById(request.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + request.getProductId()));

            if (request.getQuantity() > product.getStock()) {
                throw new InsufficientStockException(request.getQuantity(), product.getStock());
            }

            product.setStock(product.getStock() - request.getQuantity());
            productRepository.save(product);

            Order order = new Order();
            order.setCustomerName(request.getCustomerName());
            order.setProduct(product);
            order.setQuantity(request.getQuantity());
            order.setUnitPrice(request.getUnitPrice());
            order.setStatus(request.getStatus());
            return orderRepository.save(order);
        });
    }

    public Order get(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
    }

    public List<Order> list() {
        return orderRepository.findAll();
    }

    @Transactional
    public Order cancel(Long id) {
        return executeWithRetry(() -> {
            Order order = get(id);
            if (order.getStatus() == OrderStatus.CANCELLED) {
                return order;
            }
            Product product = order.getProduct();
            product.setStock(product.getStock() + order.getQuantity());
            productRepository.save(product);
            order.setStatus(OrderStatus.CANCELLED);
            return orderRepository.save(order);
        });
    }

    @Transactional
    public Order update(Long id, OrderRequest request) {
        return executeWithRetry(() -> {
            Order order = get(id);
            Long oldProductId = order.getProduct().getId();
            Integer oldQuantity = order.getQuantity();
            Long newProductId = request.getProductId();
            Integer newQuantity = request.getQuantity();

            Product newProduct = productRepository.findById(newProductId)
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + newProductId));

            if (oldProductId.equals(newProductId)) {
                int delta = newQuantity - oldQuantity;
                if (delta > 0) {
                    if (delta > newProduct.getStock()) {
                        throw new InsufficientStockException(delta, newProduct.getStock());
                    }
                    newProduct.setStock(newProduct.getStock() - delta);
                    productRepository.save(newProduct);
                } else if (delta < 0) {
                    newProduct.setStock(newProduct.getStock() - delta);
                    productRepository.save(newProduct);
                }
            } else {
                if (newQuantity > newProduct.getStock()) {
                    throw new InsufficientStockException(newQuantity, newProduct.getStock());
                }
                Product oldProduct = order.getProduct();
                oldProduct.setStock(oldProduct.getStock() + oldQuantity);
                newProduct.setStock(newProduct.getStock() - newQuantity);
                productRepository.save(oldProduct);
                productRepository.save(newProduct);
            }

            order.setCustomerName(request.getCustomerName());
            order.setProduct(newProduct);
            order.setQuantity(newQuantity);
            order.setUnitPrice(request.getUnitPrice());
            order.setStatus(request.getStatus());
            return orderRepository.save(order);
        });
    }


    @Transactional
    public void delete(Long id) {
        executeWithRetry(() -> {
            Order order = get(id);
            if (order.getStatus() != OrderStatus.CANCELLED) {
                Product product = order.getProduct();
                product.setStock(product.getStock() + order.getQuantity());
                productRepository.save(product);
            }
            orderRepository.delete(order);
            return null;
        });
    }


    /**
     * Runs the given operation, retrying a bounded number of times if a concurrent
     * modification is detected via optimistic locking on Product. Each retry re-reads
     * the affected entities (via the operation itself, which looks them up fresh) rather
     * than reusing stale in-memory state.
     */
    private <T> T executeWithRetry(Supplier<T> operation) {
        ObjectOptimisticLockingFailureException lastFailure = null;
        for (int attempt = 0; attempt < MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                return operation.get();
            } catch (ObjectOptimisticLockingFailureException ex) {
                lastFailure = ex;
            }
        }
        throw lastFailure;
    }
}
