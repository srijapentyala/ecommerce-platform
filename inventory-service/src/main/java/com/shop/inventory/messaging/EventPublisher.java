package com.shop.inventory.messaging;

public interface EventPublisher {
    void publish(String topic, String key, Object event);
}
