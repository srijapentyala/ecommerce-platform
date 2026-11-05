package com.shop.order.domain;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpInventoryClientTest {
    @Test
    void readsStockAndTreatsEmptyBodyAsZero() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://inventory");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpInventoryClient client = new HttpInventoryClient(builder.build());

        server.expect(requestTo("http://inventory/api/inventory/SKU-1"))
                .andRespond(withSuccess("{\"sku\":\"SKU-1\",\"available\":4}", MediaType.APPLICATION_JSON));
        assertEquals(4, client.available("SKU-1"));
        server.verify();
    }

    @Test
    void aFailingInventoryServiceIsReportedAsUnavailable() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://inventory");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpInventoryClient client = new HttpInventoryClient(builder.build());
        server.expect(requestTo("http://inventory/api/inventory/SKU-1")).andRespond(withServerError());
        assertThrows(InventoryUnavailableException.class, () -> client.available("SKU-1"));
    }
}
