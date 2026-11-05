package com.shop.search.api;

import com.shop.search.domain.Hit;
import com.shop.search.domain.SearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SearchController.class)
class SearchControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SearchService search;

    @Test
    void indexesAndSearches() throws Exception {
        when(search.search("mug", 5)).thenReturn(List.of(new Hit("MUG", "Ceramic Mug", 0.9)));
        mvc.perform(get("/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("up"));
        mvc.perform(post("/api/products").contentType("application/json")
                        .content("{\"sku\":\"MUG\",\"name\":\"Ceramic Mug\",\"description\":\"blue\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("MUG"));
        mvc.perform(get("/api/search").param("q", "mug"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sku").value("MUG"));
        verify(search).index(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void mapsBadQueries() throws Exception {
        doThrow(new IllegalArgumentException("query is required")).when(search).search(" ", 5);
        mvc.perform(get("/api/search").param("q", " ")).andExpect(status().isBadRequest());
    }
}
