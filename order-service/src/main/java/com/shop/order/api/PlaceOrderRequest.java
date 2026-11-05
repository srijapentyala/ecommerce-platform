package com.shop.order.api;

public record PlaceOrderRequest(String sku, int qty, long amountCents, String customerId) {}
