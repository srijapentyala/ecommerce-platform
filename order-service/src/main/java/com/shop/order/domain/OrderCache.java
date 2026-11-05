package com.shop.order.domain;

import java.util.Optional;

public interface OrderCache {
    Optional<Order> get(String id);

    void put(Order order);

    void evict(String id);
}
