package com.shop.search.messaging;

import com.shop.common.ProductUpserted;
import com.shop.common.Topics;
import com.shop.search.domain.SearchService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ProductListener {
    private final SearchService search;

    public ProductListener(SearchService search) {
        this.search = search;
    }

    @KafkaListener(topics = Topics.PRODUCTS)
    public void onProduct(ProductUpserted event) {
        search.index(event);
    }
}
