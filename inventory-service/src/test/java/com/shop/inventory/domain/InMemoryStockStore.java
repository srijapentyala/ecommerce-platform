package com.shop.inventory.domain;

import java.util.HashMap;
import java.util.Map;

class InMemoryStockStore implements StockStore {
    private record Held(String sku, int qty, Reservation state) {}

    private final Map<String, Integer> stock = new HashMap<>();
    private final Map<String, Held> held = new HashMap<>();

    @Override
    public int add(String sku, int qty) {
        return stock.merge(sku, qty, Integer::sum);
    }

    @Override
    public int available(String sku) {
        return stock.getOrDefault(sku, 0);
    }

    @Override
    public Reservation reserve(String orderId, String sku, int qty) {
        Held existing = held.get(orderId);
        if (existing != null) {
            return existing.state();
        }
        boolean ok = available(sku) >= qty;
        if (ok) {
            stock.put(sku, available(sku) - qty);
        }
        Reservation state = ok ? Reservation.RESERVED : Reservation.REJECTED;
        held.put(orderId, new Held(sku, qty, state));
        return state;
    }

    @Override
    public boolean release(String orderId) {
        Held existing = held.get(orderId);
        if (existing == null || existing.state() != Reservation.RESERVED) {
            return false;
        }
        stock.merge(existing.sku(), existing.qty(), Integer::sum);
        held.put(orderId, new Held(existing.sku(), existing.qty(), Reservation.RELEASED));
        return true;
    }
}
