package com.ecomtest.service;

import com.ecomtest.entity.Order;
import com.ecomtest.entity.Product;
import com.ecomtest.entity.Reservation;
import com.ecomtest.entity.ReservationStatus;
import com.ecomtest.exception.StockUnavailableException;
import com.ecomtest.repository.ProductRepository;
import com.ecomtest.repository.ReservationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Reserves/adjusts/commits/releases Product stock on behalf of orders.
 *
 * Concurrency control: optimistic locking via {@code @Version} on {@link Product}, combined
 * with a bounded explicit retry loop (see {@link #adjustStock(Long, int)}). Optimistic locking
 * was chosen over pessimistic row locking because checkout traffic is typically read-heavy with
 * relatively rare write conflicts, and optimistic locking avoids serializing all checkout
 * requests on a single hot product. See docs/adr/0001-stock-concurrency-control.md for the full
 * rationale.
 */
@Service
public class ReservationService {

    private final ProductRepository productRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationService self;
    private final int maxRetries;
    private final int timeoutMinutes;

    public ReservationService(ProductRepository productRepository,
                               ReservationRepository reservationRepository,
                               @Lazy ReservationService self,
                               @Value("${reservation.max-retries:3}") int maxRetries,
                               @Value("${reservation.timeout-minutes:15}") int timeoutMinutes) {
        this.productRepository = productRepository;
        this.reservationRepository = reservationRepository;
        this.self = self;
        this.maxRetries = maxRetries;
        this.timeoutMinutes = timeoutMinutes;
    }

    public Reservation reserve(Order order, Product product, int quantity) {
        adjustStock(product.getId(), -quantity);

        Reservation reservation = new Reservation();
        reservation.setOrder(order);
        reservation.setProduct(product);
        reservation.setReservedQuantity(quantity);
        reservation.setCancelledQuantity(0);
        reservation.setStatus(ReservationStatus.ACTIVE);
        reservation.setReservedUntil(Instant.now().plus(timeoutMinutes, ChronoUnit.MINUTES));
        reservation.setCreatedAt(Instant.now());
        return reservationRepository.save(reservation);
    }

    public void adjustQuantity(Reservation reservation, int newQuantity) {
        int currentlyReserved = reservation.getReservedQuantity() - reservation.getCancelledQuantity();
        int delta = newQuantity - currentlyReserved;
        if (delta == 0) {
            return;
        }

        if (delta > 0) {
            adjustStock(reservation.getProduct().getId(), -delta);
            reservation.setReservedQuantity(reservation.getReservedQuantity() + delta);
        } else {
            int releaseAmount = -delta;
            adjustStock(reservation.getProduct().getId(), releaseAmount);
            reservation.setCancelledQuantity(reservation.getCancelledQuantity() + releaseAmount);
        }
        reservationRepository.save(reservation);
    }

    public void commit(Reservation reservation) {
        reservation.setStatus(ReservationStatus.COMMITTED);
        reservation.setReservedUntil(null);
        reservationRepository.save(reservation);
    }

    public void release(Reservation reservation, Integer quantity) {
        int remaining = reservation.getReservedQuantity() - reservation.getCancelledQuantity();
        int amountToRelease = (quantity == null) ? remaining : Math.min(quantity, remaining);
        if (amountToRelease <= 0) {
            return;
        }

        adjustStock(reservation.getProduct().getId(), amountToRelease);
        reservation.setCancelledQuantity(reservation.getCancelledQuantity() + amountToRelease);
        if (reservation.getCancelledQuantity() >= reservation.getReservedQuantity()) {
            reservation.setStatus(ReservationStatus.RELEASED);
            reservation.setReservedUntil(null);
        }
        reservationRepository.save(reservation);
    }

    public List<Reservation> releaseExpired() {
        List<Reservation> expired = reservationRepository
                .findByStatusAndReservedUntilBefore(ReservationStatus.ACTIVE, Instant.now());
        for (Reservation reservation : expired) {
            release(reservation, null);
            reservation.setStatus(ReservationStatus.EXPIRED);
            reservationRepository.save(reservation);
        }
        return expired;
    }

    /**
     * Applies {@code delta} to the product's stock (negative to reserve, positive to release),
     * retrying up to {@code maxRetries} times when an optimistic lock conflict occurs. Each
     * attempt runs in its own fresh transaction ({@link #applyStockDelta}) so a lost-update
     * conflict surfaces immediately and can be retried against a freshly reloaded Product.
     */
    private void adjustStock(Long productId, int delta) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                self.applyStockDelta(productId, delta);
                return;
            } catch (ObjectOptimisticLockingFailureException ex) {
                if (attempt == maxRetries) {
                    throw new StockUnavailableException("item no longer available");
                }
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void applyStockDelta(Long productId, int delta) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new StockUnavailableException("item no longer available"));
        int newStock = product.getStock() + delta;
        if (newStock < 0) {
            throw new StockUnavailableException("item no longer available");
        }
        product.setStock(newStock);
        productRepository.saveAndFlush(product);
    }
}
