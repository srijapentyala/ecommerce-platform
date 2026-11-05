package com.shop.common;

public record InventoryReserved(String orderId, String sku, int qty, long amountCents) {}
