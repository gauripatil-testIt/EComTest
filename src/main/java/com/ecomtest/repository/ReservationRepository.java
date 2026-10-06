package com.ecomtest.repository;

import com.ecomtest.entity.Reservation;
import com.ecomtest.entity.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    Optional<Reservation> findByOrderId(Long orderId);

    List<Reservation> findByStatusAndReservedUntilBefore(ReservationStatus status, Instant instant);
}
