package com.ecomtest.email;

import com.ecomtest.entity.OrderStatus;
import com.ecomtest.event.OrderStatusChangedEvent;
import freemarker.template.TemplateExceptionHandler;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderEmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private OrderEmailService orderEmailService;

    @BeforeEach
    void setUp() {
        freemarker.template.Configuration configuration =
                new freemarker.template.Configuration(freemarker.template.Configuration.VERSION_2_3_32);
        configuration.setClassLoaderForTemplateLoading(getClass().getClassLoader(), "templates/email/");
        configuration.setDefaultEncoding("UTF-8");
        configuration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        configuration.setLogTemplateExceptions(false);
        configuration.setWrapUncheckedExceptions(true);
        configuration.setFallbackOnNullLoopVariable(false);

        orderEmailService = new OrderEmailService(mailSender, configuration);

        when(mailSender.createMimeMessage()).thenAnswer(invocation -> newMimeMessage());
    }

    private MimeMessage newMimeMessage() {
        Session session = Session.getInstance(new Properties());
        return new MimeMessage(session);
    }

    private OrderStatusChangedEvent eventFor(Long orderId, OrderStatus previous, OrderStatus newStatus,
                                              String trackingNumber) {
        return new OrderStatusChangedEvent(
                orderId,
                previous,
                newStatus,
                "jane@example.com",
                "Jane Doe",
                "Wireless Mouse",
                2,
                BigDecimal.valueOf(19.99),
                trackingNumber);
    }

    private String bodyOf(MimeMessage message) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        message.writeTo(out);
        return out.toString();
    }

    @Test
    void sendsTemplatedContentSpecificToPendingStatus() throws Exception {
        OrderStatusChangedEvent event = eventFor(1L, null, OrderStatus.PENDING, null);

        orderEmailService.sendStatusEmail(event);

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        String body = bodyOf(captor.getValue());
        assertThat(body).containsIgnoringCase("pending");
    }

    @Test
    void sendsTemplatedContentSpecificToConfirmedStatus() throws Exception {
        OrderStatusChangedEvent event = eventFor(2L, OrderStatus.PENDING, OrderStatus.CONFIRMED, null);

        orderEmailService.sendStatusEmail(event);

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        String body = bodyOf(captor.getValue());
        assertThat(body).containsIgnoringCase("confirmed");
    }

    @Test
    void sendsTemplatedContentSpecificToShippedStatusWithTrackingNumber() throws Exception {
        OrderStatusChangedEvent event = eventFor(3L, OrderStatus.CONFIRMED, OrderStatus.SHIPPED, "TRACK-123");

        orderEmailService.sendStatusEmail(event);

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        String body = bodyOf(captor.getValue());
        assertThat(body).containsIgnoringCase("shipped");
        assertThat(body).contains("TRACK-123");
    }

    @Test
    void shippedEventWithBlankTrackingNumberOmitsTrackingLine() throws Exception {
        OrderStatusChangedEvent event = eventFor(4L, OrderStatus.CONFIRMED, OrderStatus.SHIPPED, "   ");

        orderEmailService.sendStatusEmail(event);

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        String body = bodyOf(captor.getValue());
        assertThat(body).doesNotContain("Tracking Number");
    }

    @Test
    void shippedEventWithNullTrackingNumberOmitsTrackingLine() throws Exception {
        OrderStatusChangedEvent event = eventFor(5L, OrderStatus.CONFIRMED, OrderStatus.SHIPPED, null);

        orderEmailService.sendStatusEmail(event);

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        String body = bodyOf(captor.getValue());
        assertThat(body).doesNotContain("Tracking Number");
    }

    @Test
    void sendsTemplatedContentSpecificToDeliveredStatus() throws Exception {
        OrderStatusChangedEvent event = eventFor(6L, OrderStatus.SHIPPED, OrderStatus.DELIVERED, "TRACK-456");

        orderEmailService.sendStatusEmail(event);

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        String body = bodyOf(captor.getValue());
        assertThat(body).containsIgnoringCase("delivered");
    }

    @Test
    void sendsTemplatedContentSpecificToCancelledStatus() throws Exception {
        OrderStatusChangedEvent event = eventFor(7L, OrderStatus.PENDING, OrderStatus.CANCELLED, null);

        orderEmailService.sendStatusEmail(event);

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        String body = bodyOf(captor.getValue());
        assertThat(body).containsIgnoringCase("cancelled");
    }

    @Test
    void doesNotSendDuplicateEmailForSameOrderAndStatusTransition() {
        OrderStatusChangedEvent event = eventFor(8L, OrderStatus.PENDING, OrderStatus.CONFIRMED, null);

        orderEmailService.sendStatusEmail(event);
        orderEmailService.sendStatusEmail(event);

        verify(mailSender, times(1)).send(org.mockito.ArgumentMatchers.any(MimeMessage.class));
    }
}
