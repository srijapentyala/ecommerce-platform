package com.shop.payment.messaging;

import com.shop.common.InventoryReserved;
import com.shop.common.PaymentCompleted;
import com.shop.payment.domain.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessagingTest {
    @Test
    void publisherAndListener() {
        KafkaTemplate<String, Object> kafka = mock(KafkaTemplate.class);
        new KafkaEventPublisher(kafka).publish("payments", "o1", "body");
        verify(kafka).send("payments", "o1", "body");

        PaymentService payments = mock(PaymentService.class);
        InventoryReserved reserved = new InventoryReserved("o1", "SKU", 1, 100);
        when(payments.charge(reserved)).thenReturn(new PaymentCompleted("o1", true, "authorized"));
        new ReservationListener(payments).onReserved(reserved);
        verify(payments).charge(reserved);
    }
}
