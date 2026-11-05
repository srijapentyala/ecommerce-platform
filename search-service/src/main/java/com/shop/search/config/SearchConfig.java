package com.shop.search.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.search.domain.RedisSearchCache;
import com.shop.search.domain.SearchCache;
import com.shop.search.domain.SearchService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class SearchConfig {
    @Bean
    SearchCache searchCache(StringRedisTemplate redis, ObjectMapper mapper) {
        return new RedisSearchCache(redis, mapper);
    }

    @Bean
    SearchService searchService(SearchCache cache) {
        return new SearchService(cache);
    }
}
