package com.shop.inventory.api;

public record AddProductRequest(String sku, String name, String description, int qty) {}
