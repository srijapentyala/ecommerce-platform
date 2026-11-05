package com.shop.inventory.messaging;

import com.shop.common.OrderPlaced;
import com.shop.common.PaymentCompleted;
import com.shop.inventory.domain.InventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MessagingTest {
    @Test
    void publisherAndListener() {
        KafkaTemplate<String, Object> kafka = mock(KafkaTemplate.class);
        new KafkaEventPublisher(kafka).publish("products", "SKU", "body");
        verify(kafka).send("products", "SKU", "body");

        InventoryService inventory = mock(InventoryService.class);
        OrderPlaced order = new OrderPlaced("o1", "SKU", 1, 10, "c");
        new OrderListener(inventory).onOrder(order);
        verify(inventory).onOrder(order);

        PaymentCompleted payment = new PaymentCompleted("o1", false, "declined");
        new OrderListener(inventory).onPayment(payment);
        verify(inventory).onPayment(payment);
    }
}
