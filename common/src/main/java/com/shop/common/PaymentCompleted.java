package com.shop.common;

public record PaymentCompleted(String orderId, boolean success, String reason) {}
