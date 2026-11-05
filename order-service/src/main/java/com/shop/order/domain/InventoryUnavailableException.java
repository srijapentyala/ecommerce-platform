package com.shop.order.domain;

public class InventoryUnavailableException extends RuntimeException {
    public InventoryUnavailableException(Throwable cause) {
        super("inventory service unavailable", cause);
    }
}
