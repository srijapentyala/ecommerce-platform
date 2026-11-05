package com.shop.order.domain;

import java.util.Optional;

public interface OrderRepository {
    void insert(Order order);

    Optional<Order> find(String id);

    /**
     * Moves an order to a new status only if it is still in the expected one. This is atomic
     * across replicas, and it makes a redelivered event harmless: the second attempt changes
     * nothing and returns false.
     */
    boolean transition(String id, OrderStatus from, OrderStatus to);
}
