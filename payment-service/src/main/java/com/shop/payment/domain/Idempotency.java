package com.shop.payment.domain;

public interface Idempotency {
    boolean firstTime(String orderId);
}
