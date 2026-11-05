package com.shop.payment.messaging;

import com.shop.common.InventoryReserved;
import com.shop.common.Topics;
import com.shop.payment.domain.PaymentService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ReservationListener {
    private final PaymentService payments;

    public ReservationListener(PaymentService payments) {
        this.payments = payments;
    }

    @KafkaListener(topics = Topics.INVENTORY_RESERVED)
    public void onReserved(InventoryReserved event) {
        payments.charge(event);
    }
}
