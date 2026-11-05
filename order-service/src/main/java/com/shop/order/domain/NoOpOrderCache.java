package com.shop.order.domain;

import java.util.Optional;

/** Used when {@code shop.cache.enabled=false}, so the cache's effect can be measured. */
public class NoOpOrderCache implements OrderCache {
    @Override
    public Optional<Order> get(String id) {
        return Optional.empty();
    }

    @Override
    public void put(Order order) {}

    @Override
    public void evict(String id) {}
}
