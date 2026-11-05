package com.shop.order.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.common.cache.RedisJsonCache;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;

public class RedisOrderCache implements OrderCache {
    private final RedisJsonCache cache;

    public RedisOrderCache(StringRedisTemplate redis, ObjectMapper mapper) {
        this.cache = new RedisJsonCache(redis, mapper, Duration.ofMinutes(10));
    }

    RedisOrderCache(RedisJsonCache cache) {
        this.cache = cache;
    }

    @Override
    public Optional<Order> get(String id) {
        return cache.get(key(id), Order.class);
    }

    @Override
    public void put(Order order) {
        cache.put(key(order.id()), order);
    }

    @Override
    public void evict(String id) {
        cache.evict(key(id));
    }

    private static String key(String id) {
        return "order:" + id;
    }
}
