package com.ecomtest.service;

import com.ecomtest.dto.OrderRequest;
import com.ecomtest.entity.Order;
import com.ecomtest.entity.OrderStatus;
import com.ecomtest.entity.Product;
import com.ecomtest.event.OrderStatusChangedEvent;
import com.ecomtest.repository.OrderRepository;
import com.ecomtest.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private OrderService orderService;

    private Product product;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, productRepository, eventPublisher);

        product = new Product();
        product.setId(1L);
        product.setName("Wireless Mouse");
        product.setSku("SKU-1");
        product.setPrice(BigDecimal.valueOf(19.99));
        product.setStock(100);

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private OrderRequest requestFor(OrderStatus status) {
        OrderRequest request = new OrderRequest();
        request.setCustomerName("Jane Doe");
        request.setProductId(1L);
        request.setQuantity(2);
        request.setUnitPrice(BigDecimal.valueOf(19.99));
        request.setStatus(status);
        request.setCustomerEmail("jane@example.com");
        return request;
    }

    private Order existingOrder(Long id, OrderStatus status) {
        Order order = new Order();
        order.setId(id);
        order.setCustomerName("Jane Doe");
        order.setProduct(product);
        order.setQuantity(2);
        order.setUnitPrice(BigDecimal.valueOf(19.99));
        order.setStatus(status);
        order.setCustomerEmail("jane@example.com");
        return order;
    }

    @Test
    void createAlwaysPublishesEventWithNullPreviousStatus() {
        OrderRequest request = requestFor(OrderStatus.PENDING);

        orderService.create(request);

        ArgumentCaptor<OrderStatusChangedEvent> captor = ArgumentCaptor.forClass(OrderStatusChangedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());

        OrderStatusChangedEvent event = captor.getValue();
        assertThat(event.previousStatus()).isNull();
        assertThat(event.newStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void updatePublishesEventWhenStatusChangesToNonPending() {
        Long id = 1L;
        Order existing = existingOrder(id, OrderStatus.CONFIRMED);
        when(orderRepository.findById(id)).thenReturn(Optional.of(existing));

        OrderRequest request = requestFor(OrderStatus.SHIPPED);

        orderService.update(id, request);

        ArgumentCaptor<OrderStatusChangedEvent> captor = ArgumentCaptor.forClass(OrderStatusChangedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());

        OrderStatusChangedEvent event = captor.getValue();
        assertThat(event.previousStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(event.newStatus()).isEqualTo(OrderStatus.SHIPPED);
    }

    @Test
    void updateDoesNotPublishEventWhenNewStatusIsPending() {
        Long id = 1L;
        Order existing = existingOrder(id, OrderStatus.CONFIRMED);
        when(orderRepository.findById(id)).thenReturn(Optional.of(existing));

        OrderRequest request = requestFor(OrderStatus.PENDING);

        orderService.update(id, request);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void updateDoesNotPublishEventWhenStatusUnchanged() {
        Long id = 1L;
        Order existing = existingOrder(id, OrderStatus.CONFIRMED);
        when(orderRepository.findById(id)).thenReturn(Optional.of(existing));

        OrderRequest request = requestFor(OrderStatus.CONFIRMED);

        orderService.update(id, request);

        verify(eventPublisher, never()).publishEvent(any());
    }
}
