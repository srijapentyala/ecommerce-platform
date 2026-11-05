package com.shop.inventory.domain;

import com.shop.common.InventoryRejected;
import com.shop.common.InventoryReserved;
import com.shop.common.OrderPlaced;
import com.shop.common.PaymentCompleted;
import com.shop.common.ProductUpserted;
import com.shop.common.Topics;
import com.shop.inventory.messaging.EventPublisher;

public class InventoryService {
    private final StockStore stock;
    private final EventPublisher events;

    public InventoryService(StockStore stock, EventPublisher events) {
        this.stock = stock;
        this.events = events;
    }

    public int add(String sku, String name, String description, int qty) {
        if (sku == null || sku.isBlank() || name == null || name.isBlank()) {
            throw new IllegalArgumentException("sku and name are required");
        }
        if (qty < 0) {
            throw new IllegalArgumentException("qty must be non-negative");
        }
        int updated = stock.add(sku, qty);
        events.publish(Topics.PRODUCTS, sku, new ProductUpserted(sku, name, description == null ? "" : description));
        return updated;
    }

    public int available(String sku) {
        return stock.available(sku);
    }

    // Kafka delivers at least once. A redelivered order reserves nothing new, but it does
    // publish the outcome again: the first attempt may have crashed before publishing, and
    // the consumers downstream are idempotent, so a repeat costs nothing.
    public void onOrder(OrderPlaced order) {
        switch (stock.reserve(order.orderId(), order.sku(), order.qty())) {
            case RESERVED -> events.publish(Topics.INVENTORY_RESERVED, order.orderId(),
                    new InventoryReserved(order.orderId(), order.sku(), order.qty(), order.amountCents()));
            case REJECTED -> events.publish(Topics.INVENTORY_REJECTED, order.orderId(),
                    new InventoryRejected(order.orderId(), order.sku(), "insufficient stock"));
            case RELEASED -> { }
        }
    }

    /** The compensating step of the saga: a failed payment puts the stock back. */
    public void onPayment(PaymentCompleted payment) {
        if (!payment.success()) {
            stock.release(payment.orderId());
        }
    }
}
