package com.shop.order.domain;

import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public class HttpInventoryClient implements InventoryClient {
    private final RestClient http;

    public HttpInventoryClient(RestClient http) {
        this.http = http;
    }

    @Override
    public int available(String sku) {
        try {
            StockView body = http.get().uri("/api/inventory/{sku}", sku).retrieve().body(StockView.class);
            return body == null ? 0 : body.available();
        } catch (RestClientException e) {
            throw new InventoryUnavailableException(e);
        }
    }

    public record StockView(String sku, int available) {}
}
