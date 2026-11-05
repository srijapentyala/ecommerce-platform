package com.shop.inventory.api;

import com.shop.inventory.domain.InventoryService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class InventoryController {
    private final InventoryService inventory;

    public InventoryController(InventoryService inventory) {
        this.inventory = inventory;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "up");
    }

    @GetMapping("/api/inventory/{sku}")
    public StockView available(@PathVariable String sku) {
        return new StockView(sku, inventory.available(sku));
    }

    @PostMapping("/api/products")
    @ResponseStatus(HttpStatus.CREATED)
    public StockView add(@RequestBody AddProductRequest request) {
        int available = inventory.add(request.sku(), request.name(), request.description(), request.qty());
        return new StockView(request.sku(), available);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> badRequest(IllegalArgumentException ex) {
        return Map.of("error", ex.getMessage());
    }
}
