package com.ecomtest.service;

import com.ecomtest.dto.OrderRequest;
import com.ecomtest.entity.Order;
import com.ecomtest.entity.OrderStatus;
import com.ecomtest.entity.Product;
import com.ecomtest.entity.Reservation;
import com.ecomtest.exception.ResourceNotFoundException;
import com.ecomtest.repository.OrderRepository;
import com.ecomtest.repository.ProductRepository;
import com.ecomtest.repository.ReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationService reservationService;

    public OrderService(OrderRepository orderRepository,
                         ProductRepository productRepository,
                         ReservationRepository reservationRepository,
                         ReservationService reservationService) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.reservationRepository = reservationRepository;
        this.reservationService = reservationService;
    }

    @Transactional
    public Order create(OrderRequest request) {
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + request.getProductId()));

        Order order = new Order();
        order.setCustomerName(request.getCustomerName());
        order.setProduct(product);
        order.setQuantity(request.getQuantity());
        order.setUnitPrice(request.getUnitPrice());
        order.setStatus(request.getStatus());
        Order saved = orderRepository.save(order);

        reservationService.reserve(saved, product, request.getQuantity());

        return saved;
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
        Reservation reservation = reservationRepository.findByOrderId(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found for order: " + id));

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + request.getProductId()));

        Integer previousQuantity = order.getQuantity();
        OrderStatus previousStatus = order.getStatus();

        order.setCustomerName(request.getCustomerName());
        order.setProduct(product);
        order.setQuantity(request.getQuantity());
        order.setUnitPrice(request.getUnitPrice());
        order.setStatus(request.getStatus());

        if (request.getStatus() == OrderStatus.CANCELLED && previousStatus != OrderStatus.CANCELLED) {
            reservationService.release(reservation, null);
        } else {
            if (!request.getQuantity().equals(previousQuantity)) {
                reservationService.adjustQuantity(reservation, request.getQuantity());
            }
            if (request.getStatus() == OrderStatus.CONFIRMED && previousStatus != OrderStatus.CONFIRMED) {
                reservationService.commit(reservation);
            }
        }

        return orderRepository.save(order);
    }

    @Transactional
    public void delete(Long id) {
        Order order = get(id);
        reservationRepository.findByOrderId(id)
                .ifPresent(reservation -> reservationService.release(reservation, null));
        orderRepository.delete(order);
    }
}
