package com.shop.search.api;

import com.shop.common.ProductUpserted;
import com.shop.search.domain.Hit;
import com.shop.search.domain.SearchService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class SearchController {
    private final SearchService search;

    public SearchController(SearchService search) {
        this.search = search;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "up");
    }

    @PostMapping("/api/products")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> index(@RequestBody ProductUpserted product) {
        search.index(product);
        return Map.of("sku", product.sku());
    }

    @GetMapping("/api/search")
    public List<Hit> search(@RequestParam String q, @RequestParam(defaultValue = "5") int limit) {
        return search.search(q, limit);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> badRequest(IllegalArgumentException ex) {
        return Map.of("error", ex.getMessage());
    }
}
