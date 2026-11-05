package com.shop.inventory.messaging;

import com.shop.common.OrderPlaced;
import com.shop.common.PaymentCompleted;
import com.shop.common.Topics;
import com.shop.inventory.domain.InventoryService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderListener {
    private final InventoryService inventory;

    public OrderListener(InventoryService inventory) {
        this.inventory = inventory;
    }

    @KafkaListener(topics = Topics.ORDERS)
    public void onOrder(OrderPlaced event) {
        inventory.onOrder(event);
    }

    @KafkaListener(topics = Topics.PAYMENTS)
    public void onPayment(PaymentCompleted event) {
        inventory.onPayment(event);
    }
}
