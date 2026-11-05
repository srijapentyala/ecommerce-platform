package com.shop.search.domain;

import java.util.List;
import java.util.Optional;

public interface SearchCache {
    Optional<List<Hit>> get(String key);

    void put(String key, List<Hit> hits);

    void clear();
}
