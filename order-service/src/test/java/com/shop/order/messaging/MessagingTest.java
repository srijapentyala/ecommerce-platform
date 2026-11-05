package com.shop.order.messaging;

import com.shop.common.InventoryRejected;
import com.shop.common.PaymentCompleted;
import com.shop.order.domain.Order;
import com.shop.order.domain.OrderService;
import com.shop.order.domain.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessagingTest {
    @Test
    void publisherSendsTheEvent() {
        KafkaTemplate<String, Object> kafka = mock(KafkaTemplate.class);
        new KafkaEventPublisher(kafka).publish("orders", "k", "body");
        verify(kafka).send("orders", "k", "body");
    }

    @Test
    void listenerForwardsPayment() {
        OrderService orders = mock(OrderService.class);
        PaymentCompleted event = new PaymentCompleted("o1", true, "authorized");
        when(orders.onPayment(event)).thenReturn(new Order("o1", "SKU", 1, 1, "c", OrderStatus.PAID));
        new PaymentListener(orders).onPayment(event);
        verify(orders).onPayment(event);
        assertEquals("o1", event.orderId());
    }

    @Test
    void listenerForwardsRejection() {
        OrderService orders = mock(OrderService.class);
        InventoryRejected event = new InventoryRejected("o1", "SKU", "none");
        new PaymentListener(orders).onRejected(event);
        verify(orders).onRejected(event);
    }
}
