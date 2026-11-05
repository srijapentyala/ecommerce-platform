package com.shop.order.domain;

public record Order(String id, String sku, int qty, long amountCents, String customerId, OrderStatus status) {}
