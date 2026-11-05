package com.shop.notification.config;

import com.shop.notification.domain.NotificationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NotificationConfig {
    @Bean
    NotificationService notificationService() {
        return new NotificationService();
    }
}
