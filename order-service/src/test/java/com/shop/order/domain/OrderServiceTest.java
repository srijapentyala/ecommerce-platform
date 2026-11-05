package com.shop.order.domain;

import com.shop.common.InventoryRejected;
import com.shop.common.OrderPlaced;
import com.shop.common.PaymentCompleted;
import com.shop.common.Topics;
import com.shop.order.messaging.EventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderServiceTest {
    private InMemoryOrderRepository repository;
    private FakeCache cache;
    private OrderService orders;
    private List<Object> published;

    @BeforeEach
    void setUp() {
        repository = new InMemoryOrderRepository();
        cache = new FakeCache();
        published = new ArrayList<>();
        InventoryClient inventory = sku -> "SKU-1".equals(sku) ? 5 : 0;
        EventPublisher events = (topic, key, event) -> {
            assertEquals(Topics.ORDERS, topic);
            published.add(event);
        };
        orders = new OrderService(repository, cache, inventory, events);
    }

    @Test
    void placesAndCachesAnOrder() {
        Order order = orders.place("SKU-1", 2, 1500, "c1");
        assertEquals(OrderStatus.PENDING, order.status());
        assertEquals(order, orders.get(order.id()));
        assertTrue(cache.hits >= 1);
        assertEquals(order.id(), ((OrderPlaced) published.get(0)).orderId());
    }

    @Test
    void loadsFromRepositoryWhenCacheMisses() {
        Order order = orders.place("SKU-1", 1, 100, "c1");
        cache.stored = null;
        Order loaded = orders.get(order.id());
        assertEquals(order.id(), loaded.id());
        assertEquals(order.id(), cache.stored.id());
    }

    @Test
    void rejectsBadInputAndStock() {
        assertThrows(IllegalArgumentException.class, () -> orders.place(" ", 1, 1, "c"));
        assertThrows(IllegalArgumentException.class, () -> orders.place("SKU-1", 0, 1, "c"));
        assertThrows(IllegalArgumentException.class, () -> orders.place("SKU-1", 1, -1, "c"));
        InsufficientStockException ex = assertThrows(InsufficientStockException.class, () -> orders.place("NOPE", 1, 1, "c"));
        assertTrue(ex.getMessage().contains("NOPE"));
    }

    @Test
    void paymentUpdatesStatusAndDropsTheCache() {
        Order order = orders.place("SKU-1", 1, 100, "c1");
        Order paid = orders.onPayment(new PaymentCompleted(order.id(), true, "authorized"));
        assertEquals(OrderStatus.PAID, paid.status());
        assertTrue(cache.evicted);
    }

    @Test
    void failedPaymentIsRecorded() {
        Order order = orders.place("SKU-1", 1, 100, "c1");
        assertEquals(OrderStatus.PAYMENT_FAILED, orders.onPayment(new PaymentCompleted(order.id(), false, "limit")).status());
    }

    @Test
    void rejectionFromInventorySettlesTheOrder() {
        Order order = orders.place("SKU-1", 1, 100, "c1");
        assertEquals(OrderStatus.REJECTED, orders.onRejected(new InventoryRejected(order.id(), "SKU-1", "no stock")).status());
        assertEquals(OrderStatus.REJECTED, orders.get(order.id()).status());
    }

    @Test
    void aSettledOrderIgnoresLaterEvents() {
        Order order = orders.place("SKU-1", 1, 100, "c1");
        orders.onPayment(new PaymentCompleted(order.id(), true, "authorized"));
        // A redelivery or a late event must not undo a settled order.
        assertEquals(OrderStatus.PAID, orders.onPayment(new PaymentCompleted(order.id(), false, "late")).status());
        assertEquals(OrderStatus.PAID, orders.onRejected(new InventoryRejected(order.id(), "SKU-1", "late")).status());
    }

    @Test
    void missingOrderAndPaymentAreNotFound() {
        assertThrows(NoSuchElementException.class, () -> orders.get("missing"));
        assertThrows(NoSuchElementException.class, () -> orders.onPayment(new PaymentCompleted("missing", true, "x")));
        assertThrows(NoSuchElementException.class, () -> orders.onRejected(new InventoryRejected("missing", "s", "x")));
    }

    private static final class FakeCache implements OrderCache {
        private Order stored;
        private int hits;
        private boolean evicted;

        @Override
        public Optional<Order> get(String id) {
            hits++;
            return Optional.ofNullable(stored != null && stored.id().equals(id) ? stored : null);
        }

        @Override
        public void put(Order order) {
            stored = order;
        }

        @Override
        public void evict(String id) {
            evicted = true;
            stored = null;
        }
    }
}
