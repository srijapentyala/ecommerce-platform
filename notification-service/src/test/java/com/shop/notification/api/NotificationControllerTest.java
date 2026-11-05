package com.shop.notification.api;

import com.shop.notification.domain.Notice;
import com.shop.notification.domain.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
class NotificationControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private NotificationService notifications;

    @Test
    void listsNoticesAndHealth() throws Exception {
        when(notifications.all()).thenReturn(List.of(new Notice("o1", "PAID", "payment authorized for o1")));
        mvc.perform(get("/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("up"));
        mvc.perform(get("/api/notifications")).andExpect(status().isOk()).andExpect(jsonPath("$[0].orderId").value("o1"));
    }
}
