package com.shop.order.messaging;

import com.shop.common.InventoryRejected;
import com.shop.common.PaymentCompleted;
import com.shop.common.Topics;
import com.shop.order.domain.OrderService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentListener {
    private final OrderService orders;

    public PaymentListener(OrderService orders) {
        this.orders = orders;
    }

    @KafkaListener(topics = Topics.PAYMENTS)
    public void onPayment(PaymentCompleted event) {
        orders.onPayment(event);
    }

    @KafkaListener(topics = Topics.INVENTORY_REJECTED)
    public void onRejected(InventoryRejected event) {
        orders.onRejected(event);
    }
}
