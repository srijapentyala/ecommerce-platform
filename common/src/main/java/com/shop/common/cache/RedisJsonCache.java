package com.shop.common.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;

/**
 * JSON values in Redis with a TTL. Callers treat a miss, a bad payload, and an unreachable
 * Redis the same way: they fall through to the system of record. A failed evict can leave a
 * stale entry until its TTL expires, which is why the TTL is short.
 */
public class RedisJsonCache {
    private static final Logger log = LoggerFactory.getLogger(RedisJsonCache.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final Duration ttl;

    public RedisJsonCache(StringRedisTemplate redis, ObjectMapper mapper, Duration ttl) {
        this.redis = redis;
        this.mapper = mapper;
        this.ttl = ttl;
    }

    public <T> Optional<T> get(String key, Class<T> type) {
        return read(key, raw -> mapper.readValue(raw, type));
    }

    public void put(String key, Object value) {
        try {
            redis.opsForValue().set(key, mapper.writeValueAsString(value), ttl);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cache write failed for " + key, e);
        } catch (DataAccessException e) {
            log.warn("cache write skipped for {}: {}", key, e.getMessage());
        }
    }

    public void evict(String key) {
        try {
            redis.delete(key);
        } catch (DataAccessException e) {
            log.warn("cache evict failed for {}, stale until TTL: {}", key, e.getMessage());
        }
    }

    private <T> Optional<T> read(String key, Reader<T> reader) {
        String raw;
        try {
            raw = redis.opsForValue().get(key);
        } catch (DataAccessException e) {
            log.warn("cache read skipped for {}: {}", key, e.getMessage());
            return Optional.empty();
        }
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(reader.read(raw));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    @FunctionalInterface
    private interface Reader<T> {
        T read(String raw) throws JsonProcessingException;
    }
}
