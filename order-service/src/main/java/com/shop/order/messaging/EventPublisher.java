package com.shop.order.messaging;

public interface EventPublisher {
    void publish(String topic, String key, Object event);
}
