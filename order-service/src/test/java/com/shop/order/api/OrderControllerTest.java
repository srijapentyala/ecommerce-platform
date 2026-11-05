package com.shop.order.api;

import com.shop.order.domain.InsufficientStockException;
import com.shop.order.domain.InventoryUnavailableException;
import com.shop.order.domain.Order;
import com.shop.order.domain.OrderService;
import com.shop.order.domain.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.NoSuchElementException;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {OrderController.class, HealthController.class})
class OrderControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private OrderService orders;

    @Test
    void placesAndReads() throws Exception {
        Order order = new Order("o1", "SKU-1", 1, 100, "c1", OrderStatus.PENDING);
        when(orders.place("SKU-1", 1, 100, "c1")).thenReturn(order);
        when(orders.get("o1")).thenReturn(order);
        mvc.perform(post("/api/orders").contentType("application/json")
                        .content("{\"sku\":\"SKU-1\",\"qty\":1,\"amountCents\":100,\"customerId\":\"c1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("o1"));
        mvc.perform(get("/api/orders/o1")).andExpect(status().isOk()).andExpect(jsonPath("$.sku").value("SKU-1"));
        mvc.perform(get("/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("up"));
    }

    @Test
    void mapsErrors() throws Exception {
        when(orders.place("SKU-1", 0, 1, "c")).thenThrow(new IllegalArgumentException("qty must be positive"));
        when(orders.place("NOPE", 1, 1, "c")).thenThrow(new InsufficientStockException("NOPE"));
        when(orders.get("missing")).thenThrow(new NoSuchElementException("missing"));
        when(orders.place("DOWN", 1, 1, "c")).thenThrow(new InventoryUnavailableException(new RuntimeException("x")));
        mvc.perform(post("/api/orders").contentType("application/json")
                        .content("{\"sku\":\"DOWN\",\"qty\":1,\"amountCents\":1,\"customerId\":\"c\"}"))
                .andExpect(status().isServiceUnavailable());
        mvc.perform(post("/api/orders").contentType("application/json")
                        .content("{\"sku\":\"SKU-1\",\"qty\":0,\"amountCents\":1,\"customerId\":\"c\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/orders").contentType("application/json")
                        .content("{\"sku\":\"NOPE\",\"qty\":1,\"amountCents\":1,\"customerId\":\"c\"}"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/orders/missing")).andExpect(status().isNotFound());
    }
}
