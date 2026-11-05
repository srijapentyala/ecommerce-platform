package com.shop.common;

public record InventoryRejected(String orderId, String sku, String reason) {}
