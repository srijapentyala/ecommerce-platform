package com.shop.notification.messaging;

import com.shop.common.InventoryRejected;
import com.shop.common.PaymentCompleted;
import com.shop.notification.domain.NotificationService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NotificationListenerTest {
    @Test
    void forwardsBothTopics() {
        NotificationService notifications = mock(NotificationService.class);
        NotificationListener listener = new NotificationListener(notifications);
        PaymentCompleted paid = new PaymentCompleted("o1", true, "authorized");
        InventoryRejected rejected = new InventoryRejected("o2", "SKU", "insufficient stock");
        listener.onPayment(paid);
        listener.onRejected(rejected);
        verify(notifications).onPayment(paid);
        verify(notifications).onRejected(rejected);
    }
}
