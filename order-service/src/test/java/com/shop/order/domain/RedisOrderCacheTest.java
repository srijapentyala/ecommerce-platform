package com.shop.order.domain;

import com.shop.common.cache.RedisJsonCache;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisOrderCacheTest {
    @Test
    void prefixesKeys() {
        RedisJsonCache json = mock(RedisJsonCache.class);
        new RedisOrderCache(mock(org.springframework.data.redis.core.StringRedisTemplate.class), new com.fasterxml.jackson.databind.ObjectMapper());
        RedisOrderCache cache = new RedisOrderCache(json);
        Order order = new Order("o1", "SKU", 1, 10, "c", OrderStatus.PENDING);
        when(json.get("order:o1", Order.class)).thenReturn(Optional.of(order));
        assertEquals(order, cache.get("o1").orElseThrow());
        cache.put(order);
        verify(json).put("order:o1", order);
        cache.evict("o1");
        verify(json).evict("order:o1");
    }

    @Test
    void theNoOpCacheNeverHoldsAnything() {
        NoOpOrderCache cache = new NoOpOrderCache();
        Order order = new Order("o1", "SKU", 1, 10, "c", OrderStatus.PENDING);
        cache.put(order);
        assertEquals(Optional.empty(), cache.get("o1"));
        cache.evict("o1");
    }
}
