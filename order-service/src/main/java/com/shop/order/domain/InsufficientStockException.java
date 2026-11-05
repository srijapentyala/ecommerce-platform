package com.shop.order.domain;

public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String sku) {
        super("insufficient stock for " + sku);
    }
}
