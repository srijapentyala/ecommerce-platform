package com.shop.common;

public record OrderPlaced(String orderId, String sku, int qty, long amountCents, String customerId) {}
