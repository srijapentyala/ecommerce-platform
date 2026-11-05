package com.shop.search.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.common.cache.RedisJsonCache;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class RedisSearchCache implements SearchCache {
    private final RedisJsonCache json;
    private final StringRedisTemplate redis;

    public RedisSearchCache(StringRedisTemplate redis, ObjectMapper mapper) {
        this(new RedisJsonCache(redis, mapper, Duration.ofSeconds(30)), redis);
    }

    RedisSearchCache(RedisJsonCache json, StringRedisTemplate redis) {
        this.json = json;
        this.redis = redis;
    }

    @Override
    public Optional<List<Hit>> get(String key) {
        return json.get(cacheKey(key), SearchHits.class).map(SearchHits::hits);
    }

    @Override
    public void put(String key, List<Hit> hits) {
        json.put(cacheKey(key), new SearchHits(hits));
    }

    @Override
    public void clear() {
        Set<String> keys = redis.keys("search:*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    private static String cacheKey(String key) {
        return "search:" + key;
    }
}
