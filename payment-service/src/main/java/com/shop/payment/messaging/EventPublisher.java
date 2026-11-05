package com.shop.payment.messaging;

public interface EventPublisher {
    void publish(String topic, String key, Object event);
}
