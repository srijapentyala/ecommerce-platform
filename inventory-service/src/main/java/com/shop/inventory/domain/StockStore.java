package com.shop.inventory.domain;

public interface StockStore {
    enum Reservation { RESERVED, REJECTED, RELEASED }

    int add(String sku, int qty);

    int available(String sku);

    /**
     * Reserves stock for an order exactly once. A repeat call for the same order changes
     * nothing and returns the outcome of the first call.
     */
    Reservation reserve(String orderId, String sku, int qty);

    /** Returns reserved stock to the shelf. True only for the call that actually released it. */
    boolean release(String orderId);
}
