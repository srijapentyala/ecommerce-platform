package com.shop.notification.messaging;

import com.shop.common.InventoryRejected;
import com.shop.common.PaymentCompleted;
import com.shop.common.Topics;
import com.shop.notification.domain.NotificationService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationListener {
    private final NotificationService notifications;

    public NotificationListener(NotificationService notifications) {
        this.notifications = notifications;
    }

    @KafkaListener(topics = Topics.PAYMENTS)
    public void onPayment(PaymentCompleted event) {
        notifications.onPayment(event);
    }

    @KafkaListener(topics = Topics.INVENTORY_REJECTED)
    public void onRejected(InventoryRejected event) {
        notifications.onRejected(event);
    }
}
