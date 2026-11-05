package com.shop.inventory.config;

import com.shop.inventory.domain.InventoryService;
import com.shop.inventory.domain.JdbcStockStore;
import com.shop.inventory.domain.StockStore;
import com.shop.inventory.messaging.EventPublisher;
import com.shop.inventory.messaging.KafkaEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class InventoryConfig {
    @Bean
    EventPublisher eventPublisher(KafkaTemplate<String, Object> kafka) {
        return new KafkaEventPublisher(kafka);
    }

    @Bean
    StockStore stockStore(JdbcClient jdbc, TransactionTemplate tx) {
        return new JdbcStockStore(jdbc, tx);
    }

    @Bean
    InventoryService inventoryService(StockStore stock, EventPublisher events) {
        return new InventoryService(stock, events);
    }
}
