package com.shop.payment.config;

import com.shop.payment.domain.Idempotency;
import com.shop.payment.domain.PaymentService;
import com.shop.payment.domain.RedisIdempotency;
import com.shop.payment.messaging.EventPublisher;
import com.shop.payment.messaging.KafkaEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;

@Configuration
public class PaymentConfig {
    @Bean
    Idempotency idempotency(StringRedisTemplate redis) {
        return new RedisIdempotency(redis);
    }

    @Bean
    EventPublisher eventPublisher(KafkaTemplate<String, Object> kafka) {
        return new KafkaEventPublisher(kafka);
    }

    @Bean
    PaymentService paymentService(Idempotency idempotency, EventPublisher events) {
        return new PaymentService(idempotency, events);
    }
}
