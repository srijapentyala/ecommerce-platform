package com.shop.notification.domain;

import com.shop.common.InventoryRejected;
import com.shop.common.PaymentCompleted;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class NotificationService {
    private final List<Notice> inbox = new CopyOnWriteArrayList<>();

    public void onPayment(PaymentCompleted event) {
        String message = event.success()
                ? "payment authorized for " + event.orderId()
                : "payment failed for " + event.orderId() + ": " + event.reason();
        inbox.add(new Notice(event.orderId(), event.success() ? "PAID" : "FAILED", message));
    }

    public void onRejected(InventoryRejected event) {
        inbox.add(new Notice(event.orderId(), "REJECTED", event.reason() + " (" + event.sku() + ")"));
    }

    public List<Notice> all() {
        return List.copyOf(inbox);
    }
}
