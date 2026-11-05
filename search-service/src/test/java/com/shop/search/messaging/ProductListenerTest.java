package com.shop.search.messaging;

import com.shop.common.ProductUpserted;
import com.shop.search.domain.SearchService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ProductListenerTest {
    @Test
    void indexesTheProduct() {
        SearchService search = mock(SearchService.class);
        ProductUpserted product = new ProductUpserted("MUG", "Ceramic Mug", "blue");
        new ProductListener(search).onProduct(product);
        verify(search).index(product);
    }
}
