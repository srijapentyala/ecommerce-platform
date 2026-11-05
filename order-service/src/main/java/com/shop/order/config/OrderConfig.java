package com.shop.order.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.order.domain.HttpInventoryClient;
import com.shop.order.domain.JdbcOrderRepository;
import com.shop.order.domain.NoOpOrderCache;
import com.shop.order.domain.InventoryClient;
import com.shop.order.domain.OrderCache;
import com.shop.order.domain.OrderRepository;
import com.shop.order.domain.OrderService;
import com.shop.order.domain.RedisOrderCache;
import com.shop.order.messaging.EventPublisher;
import com.shop.order.messaging.KafkaEventPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class OrderConfig {
    @Bean
    OrderRepository orderRepository(JdbcClient jdbc) {
        return new JdbcOrderRepository(jdbc);
    }

    @Bean
    OrderCache orderCache(@Value("${shop.cache.enabled:true}") boolean enabled, StringRedisTemplate redis, ObjectMapper mapper) {
        return enabled ? new RedisOrderCache(redis, mapper) : new NoOpOrderCache();
    }

    @Bean
    InventoryClient inventoryClient(@Value("${shop.inventory-url}") String baseUrl) {
        SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(Duration.ofMillis(500));
        requests.setReadTimeout(Duration.ofSeconds(2));
        return new HttpInventoryClient(RestClient.builder().baseUrl(baseUrl).requestFactory(requests).build());
    }

    @Bean
    EventPublisher eventPublisher(KafkaTemplate<String, Object> kafka) {
        return new KafkaEventPublisher(kafka);
    }

    @Bean
    OrderService orderService(OrderRepository repository, OrderCache cache, InventoryClient inventory, EventPublisher events) {
        return new OrderService(repository, cache, inventory, events);
    }
}
