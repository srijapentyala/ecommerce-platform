package com.shop.notification.domain;

import com.shop.common.InventoryRejected;
import com.shop.common.PaymentCompleted;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationServiceTest {
    @Test
    void recordsPaymentsAndRejections() {
        NotificationService notifications = new NotificationService();
        assertTrue(notifications.all().isEmpty());
        notifications.onPayment(new PaymentCompleted("o1", true, "authorized"));
        notifications.onPayment(new PaymentCompleted("o2", false, "amount out of range"));
        notifications.onRejected(new InventoryRejected("o3", "SKU", "insufficient stock"));
        assertEquals(3, notifications.all().size());
        assertEquals("PAID", notifications.all().get(0).kind());
        assertEquals("FAILED", notifications.all().get(1).kind());
        assertTrue(notifications.all().get(2).message().contains("SKU"));
    }
}
