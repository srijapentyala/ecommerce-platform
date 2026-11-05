package com.shop.payment.domain;

import com.shop.common.InventoryReserved;
import com.shop.common.PaymentCompleted;
import com.shop.common.Topics;
import com.shop.payment.messaging.EventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentServiceTest {
    @Test
    void authorizesOnceAndRejectsOutOfRangeAmounts() {
        List<PaymentCompleted> published = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();
        Idempotency idempotency = id -> calls.incrementAndGet() == 1 || !"o1".equals(id);
        EventPublisher events = (topic, key, event) -> {
            assertEquals(Topics.PAYMENTS, topic);
            published.add((PaymentCompleted) event);
        };
        PaymentService payments = new PaymentService(idempotency, events);

        PaymentCompleted ok = payments.charge(new InventoryReserved("o1", "SKU", 1, 100));
        PaymentCompleted duplicate = payments.charge(new InventoryReserved("o1", "SKU", 1, 100));
        PaymentCompleted zero = payments.charge(new InventoryReserved("o2", "SKU", 1, 0));
        PaymentCompleted huge = payments.charge(new InventoryReserved("o3", "SKU", 1, PaymentService.MAX_CENTS));

        assertTrue(ok.success());
        assertEquals("authorized", ok.reason());
        assertEquals("duplicate", duplicate.reason());
        assertFalse(zero.success());
        assertFalse(huge.success());
        assertEquals(3, published.size());
    }

    @Test
    void redisStoresTheFirstChargeOnly() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        Duration ttl = Duration.ofHours(1);
        when(values.setIfAbsent("payment:o1", "1", ttl)).thenReturn(Boolean.TRUE, Boolean.FALSE, null);
        RedisIdempotency gate = new RedisIdempotency(redis, ttl);
        assertTrue(gate.firstTime("o1"));
        assertFalse(gate.firstTime("o1"));
        assertFalse(gate.firstTime("o1"));
        new RedisIdempotency(redis);
    }
}
