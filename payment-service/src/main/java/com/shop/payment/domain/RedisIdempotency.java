package com.shop.payment.domain;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

public class RedisIdempotency implements Idempotency {
    private final StringRedisTemplate redis;
    private final Duration ttl;

    public RedisIdempotency(StringRedisTemplate redis) {
        this(redis, Duration.ofDays(1));
    }

    RedisIdempotency(StringRedisTemplate redis, Duration ttl) {
        this.redis = redis;
        this.ttl = ttl;
    }

    @Override
    public boolean firstTime(String orderId) {
        Boolean stored = redis.opsForValue().setIfAbsent("payment:" + orderId, "1", ttl);
        return Boolean.TRUE.equals(stored);
    }
}
