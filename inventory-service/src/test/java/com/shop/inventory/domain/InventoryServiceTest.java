package com.shop.inventory.domain;

import com.shop.common.InventoryRejected;
import com.shop.common.InventoryReserved;
import com.shop.common.OrderPlaced;
import com.shop.common.PaymentCompleted;
import com.shop.common.ProductUpserted;
import com.shop.common.Topics;
import com.shop.inventory.messaging.EventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InventoryServiceTest {
    private List<Published> published;
    private InventoryService inventory;

    @BeforeEach
    void setUp() {
        published = new ArrayList<>();
        EventPublisher events = (topic, key, event) -> published.add(new Published(topic, key, event));
        inventory = new InventoryService(new InMemoryStockStore(), events);
    }

    @Test
    void addsStockAndPublishesTheProduct() {
        assertEquals(0, inventory.available("SKU-1"));
        assertEquals(3, inventory.add("SKU-1", "Mug", null, 3));
        assertEquals(5, inventory.add("SKU-1", "Mug", "ceramic", 2));
        ProductUpserted event = (ProductUpserted) published.get(0).event();
        assertEquals(Topics.PRODUCTS, published.get(0).topic());
        assertEquals("", event.description());
        assertEquals("ceramic", ((ProductUpserted) published.get(1).event()).description());
    }

    @Test
    void rejectsBadCatalogInput() {
        assertThrows(IllegalArgumentException.class, () -> inventory.add(" ", "Mug", "", 1));
        assertThrows(IllegalArgumentException.class, () -> inventory.add("SKU", " ", "", 1));
        assertThrows(IllegalArgumentException.class, () -> inventory.add("SKU", "Mug", "", -1));
    }

    @Test
    void reservesOrRejectsAnOrder() {
        inventory.add("SKU-1", "Mug", "ceramic", 2);
        inventory.onOrder(new OrderPlaced("o1", "SKU-1", 2, 500, "c1"));
        inventory.onOrder(new OrderPlaced("o2", "SKU-1", 1, 250, "c1"));
        inventory.onOrder(new OrderPlaced("o3", "MISSING", 1, 100, "c1"));

        InventoryReserved reserved = assertInstanceOf(InventoryReserved.class, published.get(1).event());
        assertEquals(Topics.INVENTORY_RESERVED, published.get(1).topic());
        assertEquals("o1", reserved.orderId());
        assertEquals(0, inventory.available("SKU-1"));

        InventoryRejected rejected = assertInstanceOf(InventoryRejected.class, published.get(2).event());
        assertEquals("insufficient stock", rejected.reason());
        InventoryRejected missing = assertInstanceOf(InventoryRejected.class, published.get(3).event());
        assertEquals("MISSING", missing.sku());
    }

    @Test
    void aRedeliveredOrderReservesOnceButPublishesAgain() {
        inventory.add("SKU-1", "Mug", "", 5);
        OrderPlaced order = new OrderPlaced("o1", "SKU-1", 2, 500, "c1");
        inventory.onOrder(order);
        inventory.onOrder(order);
        assertEquals(3, inventory.available("SKU-1"));
        // The repeat covers a crash between committing the reservation and publishing it.
        assertEquals(2, published.stream().filter(p -> p.topic().equals(Topics.INVENTORY_RESERVED)).count());
    }

    @Test
    void aFailedPaymentPutsTheStockBackOnce() {
        inventory.add("SKU-1", "Mug", "", 5);
        inventory.onOrder(new OrderPlaced("o1", "SKU-1", 2, 500, "c1"));
        inventory.onPayment(new PaymentCompleted("o1", false, "declined"));
        assertEquals(5, inventory.available("SKU-1"));
        inventory.onPayment(new PaymentCompleted("o1", false, "declined"));
        assertEquals(5, inventory.available("SKU-1"));
    }

    @Test
    void aSuccessfulPaymentKeepsTheStockReserved() {
        inventory.add("SKU-1", "Mug", "", 5);
        inventory.onOrder(new OrderPlaced("o1", "SKU-1", 2, 500, "c1"));
        inventory.onPayment(new PaymentCompleted("o1", true, "authorized"));
        assertEquals(3, inventory.available("SKU-1"));
    }

    @Test
    void anOrderRedeliveredAfterReleaseDoesNothing() {
        inventory.add("SKU-1", "Mug", "", 5);
        OrderPlaced order = new OrderPlaced("o1", "SKU-1", 2, 500, "c1");
        inventory.onOrder(order);
        inventory.onPayment(new PaymentCompleted("o1", false, "declined"));
        int before = published.size();
        inventory.onOrder(order);
        assertEquals(before, published.size());
        assertEquals(5, inventory.available("SKU-1"));
    }

    private record Published(String topic, String key, Object event) {}
}
