package com.ecomtest.email;

import com.ecomtest.event.OrderStatusChangedEvent;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.stereotype.Component;

/**
 * Listens for {@link OrderStatusChangedEvent}s and triggers the corresponding notification email
 * only after the order change has actually been committed, so that an email is never sent for a
 * status transition that was rolled back.
 */
@Component
public class OrderEmailListener {

    private final OrderEmailService orderEmailService;

    public OrderEmailListener(OrderEmailService orderEmailService) {
        this.orderEmailService = orderEmailService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        orderEmailService.sendStatusEmail(event);
    }
}
