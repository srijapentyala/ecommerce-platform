package com.shop.order.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs the real SQL and the real schema file against H2 in PostgreSQL mode. */
class JdbcOrderRepositoryTest {
    private JdbcOrderRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        DataSource source = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        try (Connection connection = source.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema.sql"));
        }
        repository = new JdbcOrderRepository(JdbcClient.create(source));
    }

    private static Order order(String id) {
        return new Order(id, "SKU-1", 2, 1500, "c1", OrderStatus.PENDING);
    }

    @Test
    void readsBackWhatItStored() {
        repository.insert(order("o1"));
        assertEquals(order("o1"), repository.find("o1").orElseThrow());
        assertTrue(repository.find("missing").isEmpty());
    }

    @Test
    void aDuplicateIdIsRefused() {
        repository.insert(order("o1"));
        assertThrows(RuntimeException.class, () -> repository.insert(order("o1")));
    }

    @Test
    void transitionOnlyFiresFromTheExpectedStatus() {
        repository.insert(order("o1"));
        assertTrue(repository.transition("o1", OrderStatus.PENDING, OrderStatus.PAID));
        assertFalse(repository.transition("o1", OrderStatus.PENDING, OrderStatus.PAYMENT_FAILED));
        assertEquals(OrderStatus.PAID, repository.find("o1").orElseThrow().status());
        assertFalse(repository.transition("missing", OrderStatus.PENDING, OrderStatus.PAID));
    }
}
