package com.shop.order.domain;

import com.shop.common.InventoryRejected;
import com.shop.common.OrderPlaced;
import com.shop.common.PaymentCompleted;
import com.shop.common.Topics;
import com.shop.order.messaging.EventPublisher;

import java.util.NoSuchElementException;
import java.util.UUID;

public class OrderService {
    private final OrderRepository repository;
    private final OrderCache cache;
    private final InventoryClient inventory;
    private final EventPublisher events;

    public OrderService(OrderRepository repository, OrderCache cache, InventoryClient inventory, EventPublisher events) {
        this.repository = repository;
        this.cache = cache;
        this.inventory = inventory;
        this.events = events;
    }

    public Order place(String sku, int qty, long amountCents, String customerId) {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku is required");
        }
        if (qty <= 0) {
            throw new IllegalArgumentException("qty must be positive");
        }
        if (amountCents < 0) {
            throw new IllegalArgumentException("amount must be non-negative");
        }
        if (inventory.available(sku) < qty) {
            throw new InsufficientStockException(sku);
        }
        Order order = new Order(UUID.randomUUID().toString(), sku, qty, amountCents, customerId, OrderStatus.PENDING);
        repository.insert(order);
        cache.put(order);
        events.publish(Topics.ORDERS, order.id(), new OrderPlaced(order.id(), sku, qty, amountCents, customerId));
        return order;
    }

    public Order get(String id) {
        return cache.get(id).orElseGet(() -> {
            Order order = repository.find(id).orElseThrow(() -> new NoSuchElementException(id));
            cache.put(order);
            return order;
        });
    }

    public Order onPayment(PaymentCompleted event) {
        return settle(event.orderId(), event.success() ? OrderStatus.PAID : OrderStatus.PAYMENT_FAILED);
    }

    public Order onRejected(InventoryRejected event) {
        return settle(event.orderId(), OrderStatus.REJECTED);
    }

    // PENDING is the only state an event can move an order out of. A redelivered or late
    // event finds the order already settled and leaves it alone.
    private Order settle(String id, OrderStatus outcome) {
        repository.transition(id, OrderStatus.PENDING, outcome);
        cache.evict(id);
        return repository.find(id).orElseThrow(() -> new NoSuchElementException(id));
    }
}
