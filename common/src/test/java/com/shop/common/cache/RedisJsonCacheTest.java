package com.shop.common.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.common.OrderPlaced;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisJsonCacheTest {
    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private RedisJsonCache cache;

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        cache = new RedisJsonCache(redis, new ObjectMapper(), Duration.ofMinutes(5));
    }

    @Test
    void roundTripsAValue() {
        OrderPlaced event = new OrderPlaced("o1", "SKU", 1, 100, "c1");
        cache.put("k", event);
        verify(values).set(org.mockito.ArgumentMatchers.eq("k"), org.mockito.ArgumentMatchers.contains("o1"), org.mockito.ArgumentMatchers.eq(Duration.ofMinutes(5)));

        when(values.get("k")).thenReturn("{\"orderId\":\"o1\",\"sku\":\"SKU\",\"qty\":1,\"amountCents\":100,\"customerId\":\"c1\"}");
        Optional<OrderPlaced> loaded = cache.get("k", OrderPlaced.class);
        assertEquals(event, loaded.orElseThrow());
    }

    @Test
    void missAndGarbageAreEmpty() {
        when(values.get("missing")).thenReturn(null);
        when(values.get("blank")).thenReturn("  ");
        when(values.get("bad")).thenReturn("{");
        assertTrue(cache.get("missing", OrderPlaced.class).isEmpty());
        assertTrue(cache.get("blank", OrderPlaced.class).isEmpty());
        assertTrue(cache.get("bad", OrderPlaced.class).isEmpty());
    }

    @Test
    void evictDeletesTheKey() {
        cache.evict("k");
        verify(redis).delete("k");
    }

    @Test
    void anUnreachableRedisBehavesLikeAMiss() {
        var down = new org.springframework.data.redis.RedisConnectionFailureException("down");
        when(values.get("k")).thenThrow(down);
        org.mockito.Mockito.doThrow(down).when(values).set(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(Duration.class));
        when(redis.delete("k")).thenThrow(down);

        assertTrue(cache.get("k", OrderPlaced.class).isEmpty());
        cache.put("k", "value");
        cache.evict("k");
    }

    @Test
    void writeFailureSurfaces() {
        ObjectMapper broken = mock(ObjectMapper.class);
        try {
            when(broken.writeValueAsString(org.mockito.ArgumentMatchers.any()))
                    .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("nope") {});
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new AssertionError(e);
        }
        RedisJsonCache failing = new RedisJsonCache(redis, broken, Duration.ofSeconds(1));
        assertThrows(IllegalStateException.class, () -> failing.put("k", "x"));
    }
}
