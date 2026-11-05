package com.shop.common;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TopicsTest {
    @Test
    void namesStayStable() throws Exception {
        assertEquals("orders", Topics.ORDERS);
        assertEquals("inventory-reserved", Topics.INVENTORY_RESERVED);
        assertEquals("inventory-rejected", Topics.INVENTORY_REJECTED);
        assertEquals("payments", Topics.PAYMENTS);
        assertEquals("products", Topics.PRODUCTS);
        Constructor<Topics> ctor = Topics.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        assertNotNull(ctor.newInstance());
    }

    @Test
    void eventsKeepTheirFields() {
        OrderPlaced placed = new OrderPlaced("o", "sku", 1, 10, "c");
        InventoryReserved reserved = new InventoryReserved("o", "sku", 1, 10);
        InventoryRejected rejected = new InventoryRejected("o", "sku", "short");
        PaymentCompleted paid = new PaymentCompleted("o", true, "authorized");
        ProductUpserted product = new ProductUpserted("sku", "Mug", "blue");
        assertEquals("o", placed.orderId());
        assertEquals(1, reserved.qty());
        assertEquals("short", rejected.reason());
        assertTrue(paid.success());
        assertFalse(product.description().isBlank());
    }
}
