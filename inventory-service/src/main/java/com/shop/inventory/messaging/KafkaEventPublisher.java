package com.shop.inventory.messaging;

import org.springframework.kafka.core.KafkaTemplate;

public class KafkaEventPublisher implements EventPublisher {
    private final KafkaTemplate<String, Object> kafka;

    public KafkaEventPublisher(KafkaTemplate<String, Object> kafka) {
        this.kafka = kafka;
    }

    @Override
    public void publish(String topic, String key, Object event) {
        kafka.send(topic, key, event);
    }
}
