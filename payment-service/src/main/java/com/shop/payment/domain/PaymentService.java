package com.shop.payment.domain;

import com.shop.common.InventoryReserved;
import com.shop.common.PaymentCompleted;
import com.shop.common.Topics;
import com.shop.payment.messaging.EventPublisher;

public class PaymentService {
    static final long MAX_CENTS = 500_000L;

    private final Idempotency idempotency;
    private final EventPublisher events;

    public PaymentService(Idempotency idempotency, EventPublisher events) {
        this.idempotency = idempotency;
        this.events = events;
    }

    public PaymentCompleted charge(InventoryReserved reserved) {
        if (!idempotency.firstTime(reserved.orderId())) {
            return new PaymentCompleted(reserved.orderId(), true, "duplicate");
        }
        boolean ok = reserved.amountCents() > 0 && reserved.amountCents() < MAX_CENTS;
        PaymentCompleted result = new PaymentCompleted(
                reserved.orderId(),
                ok,
                ok ? "authorized" : "amount out of range");
        events.publish(Topics.PAYMENTS, reserved.orderId(), result);
        return result;
    }
}
