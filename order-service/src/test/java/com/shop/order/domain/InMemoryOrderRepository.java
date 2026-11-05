package com.shop.order.domain;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

class InMemoryOrderRepository implements OrderRepository {
    private final Map<String, Order> rows = new ConcurrentHashMap<>();

    @Override
    public void insert(Order order) {
        rows.put(order.id(), order);
    }

    @Override
    public Optional<Order> find(String id) {
        return Optional.ofNullable(rows.get(id));
    }

    @Override
    public boolean transition(String id, OrderStatus from, OrderStatus to) {
        Order current = rows.get(id);
        if (current == null || current.status() != from) {
            return false;
        }
        rows.put(id, new Order(current.id(), current.sku(), current.qty(), current.amountCents(), current.customerId(), to));
        return true;
    }
}
