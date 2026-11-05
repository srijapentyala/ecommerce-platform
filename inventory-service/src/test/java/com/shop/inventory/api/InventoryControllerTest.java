package com.shop.inventory.api;

import com.shop.inventory.domain.InventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InventoryController.class)
class InventoryControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private InventoryService inventory;

    @Test
    void readsAddsAndReportsHealth() throws Exception {
        when(inventory.available("SKU-1")).thenReturn(4);
        when(inventory.add("SKU-1", "Mug", "ceramic", 4)).thenReturn(4);
        mvc.perform(get("/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("up"));
        mvc.perform(get("/api/inventory/SKU-1")).andExpect(status().isOk()).andExpect(jsonPath("$.available").value(4));
        mvc.perform(post("/api/products").contentType("application/json")
                        .content("{\"sku\":\"SKU-1\",\"name\":\"Mug\",\"description\":\"ceramic\",\"qty\":4}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.available").value(4));
    }

    @Test
    void mapsValidationErrors() throws Exception {
        when(inventory.add(" ", "Mug", "", 1)).thenThrow(new IllegalArgumentException("sku and name are required"));
        mvc.perform(post("/api/products").contentType("application/json")
                        .content("{\"sku\":\" \",\"name\":\"Mug\",\"description\":\"\",\"qty\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("sku and name are required"));
    }
}
