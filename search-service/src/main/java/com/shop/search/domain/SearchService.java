package com.shop.search.domain;

import com.shop.common.ProductUpserted;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

public class SearchService {
    private final ConcurrentHashMap<String, Indexed> catalog = new ConcurrentHashMap<>();
    private final SearchCache cache;

    public SearchService(SearchCache cache) {
        this.cache = cache;
    }

    public void index(ProductUpserted product) {
        if (product.sku() == null || product.sku().isBlank() || product.name() == null || product.name().isBlank()) {
            throw new IllegalArgumentException("sku and name are required");
        }
        String description = product.description() == null ? "" : product.description();
        catalog.put(product.sku(), new Indexed(product.sku(), product.name(), HashingEmbedder.embed(product.name() + " " + description)));
        cache.clear();
    }

    public List<Hit> search(String query, int limit) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query is required");
        }
        int capped = Math.clamp(limit, 1, 20);
        String key = query.trim().toLowerCase(Locale.ROOT) + ":" + capped;
        return cache.get(key).orElseGet(() -> rank(query, capped, key));
    }

    private List<Hit> rank(String query, int limit, String key) {
        double[] vector = HashingEmbedder.embed(query);
        List<Hit> hits = catalog.values().stream()
                .map(item -> new Hit(item.sku(), item.name(), HashingEmbedder.cosine(vector, item.vector())))
                .sorted(Comparator.comparingDouble(Hit::score).reversed())
                .limit(limit)
                .toList();
        cache.put(key, hits);
        return hits;
    }

    private record Indexed(String sku, String name, double[] vector) {}
}
