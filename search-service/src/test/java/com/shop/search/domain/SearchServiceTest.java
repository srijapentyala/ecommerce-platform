package com.shop.search.domain;

import com.shop.common.ProductUpserted;
import com.shop.common.cache.RedisJsonCache;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchServiceTest {
    @Test
    void ranksSharedWordsAboveUnrelatedProductsAndUsesTheCache() throws Exception {
        Constructor<HashingEmbedder> ctor = HashingEmbedder.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        assertNotNull(ctor.newInstance());
        assertEquals(0, HashingEmbedder.embed("   ")[0]);
        assertEquals(0, HashingEmbedder.embed(null)[0]);
        assertEquals(0, HashingEmbedder.embed("!!!")[0]);
        double[] same = HashingEmbedder.embed("ceramic mug");
        assertEquals(1.0, HashingEmbedder.cosine(same, HashingEmbedder.embed("ceramic mug")), 1e-9);

        MemoryCache cache = new MemoryCache();
        SearchService search = new SearchService(cache);
        assertThrows(IllegalArgumentException.class, () -> search.index(new ProductUpserted(" ", "Mug", "")));
        assertThrows(IllegalArgumentException.class, () -> search.index(new ProductUpserted("SKU", " ", "")));
        search.index(new ProductUpserted("MUG", "Ceramic Mug", null));
        search.index(new ProductUpserted("LAP", "Laptop Stand", "aluminum"));
        assertThrows(IllegalArgumentException.class, () -> search.search(" ", 5));

        List<Hit> hits = search.search("ceramic coffee mug", 1);
        assertEquals("MUG", hits.get(0).sku());
        assertEquals(1, cache.puts);
        assertEquals(hits, search.search("  Ceramic Coffee Mug  ", 1));
        assertEquals(1, cache.puts);
        assertEquals(1, search.search("mug", 0).size());
        assertEquals(2, search.search("mug", 50).size());
    }

    @Test
    void redisCacheRoundTripsAndClears() {
        RedisJsonCache json = mock(RedisJsonCache.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        RedisSearchCache cache = new RedisSearchCache(json, redis);
        Hit hit = new Hit("MUG", "Ceramic Mug", 0.8);
        when(json.get("search:mug:5", SearchHits.class)).thenReturn(Optional.of(new SearchHits(List.of(hit))));
        assertEquals("MUG", cache.get("mug:5").orElseThrow().get(0).sku());
        cache.put("mug:5", List.of(hit));
        verify(json).put(eq("search:mug:5"), any(SearchHits.class));

        new RedisSearchCache(redis, new com.fasterxml.jackson.databind.ObjectMapper());
        when(redis.keys("search:*")).thenReturn(null, Set.of(), Set.of("search:mug:5"));
        cache.clear();
        cache.clear();
        cache.clear();
        verify(redis).delete(Set.of("search:mug:5"));
    }

    private static final class MemoryCache implements SearchCache {
        private final Map<String, List<Hit>> rows = new HashMap<>();
        private int puts;

        @Override
        public Optional<List<Hit>> get(String key) {
            return Optional.ofNullable(rows.get(key));
        }

        @Override
        public void put(String key, List<Hit> hits) {
            puts++;
            rows.put(key, hits);
        }

        @Override
        public void clear() {
            rows.clear();
        }
    }
}
