package com.ecomtest.service;

import com.ecomtest.entity.Order;
import com.ecomtest.entity.OrderStatus;
import com.ecomtest.entity.Reservation;
import com.ecomtest.repository.OrderRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Periodically releases reservations whose reservedUntil timestamp has passed, returning stock
 * to the available pool and cancelling the associated order if it is still PENDING. Because the
 * expiry state (reservedUntil) is a persisted column on Reservation rather than an in-memory
 * timer, this sweep is correct even after an application restart: the next scheduled run simply
 * re-reads the current state from the database.
 */
@Component
public class ReservationExpiryJob {

    private final ReservationService reservationService;
    private final OrderRepository orderRepository;

    public ReservationExpiryJob(ReservationService reservationService, OrderRepository orderRepository) {
        this.reservationService = reservationService;
        this.orderRepository = orderRepository;
    }

    @Scheduled(fixedDelayString = "${reservation.expiry-check-delay-ms:60000}")
    public void expireReservations() {
        List<Reservation> expired = reservationService.releaseExpired();
        for (Reservation reservation : expired) {
            Order order = reservation.getOrder();
            if (order != null && order.getStatus() == OrderStatus.PENDING) {
                order.setStatus(OrderStatus.CANCELLED);
                orderRepository.save(order);
            }
        }
    }
}
