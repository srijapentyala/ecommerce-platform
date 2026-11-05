package com.shop.inventory.domain;

import com.shop.inventory.domain.StockStore.Reservation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs the real SQL and the real schema file against H2 in PostgreSQL mode. */
class JdbcStockStoreTest {
    private JdbcStockStore store;

    @BeforeEach
    void setUp() throws Exception {
        DataSource source = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=20000", "sa", "");
        try (Connection connection = source.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema.sql"));
        }
        store = new JdbcStockStore(JdbcClient.create(source), new TransactionTemplate(new DataSourceTransactionManager(source)));
    }

    @Test
    void addAccumulatesAndUnknownSkuIsZero() {
        assertEquals(0, store.available("SKU"));
        assertEquals(3, store.add("SKU", 3));
        assertEquals(5, store.add("SKU", 2));
        assertEquals(5, store.available("SKU"));
    }

    @Test
    void reservesWhileStockLastsThenRejects() {
        store.add("SKU", 3);
        assertEquals(Reservation.RESERVED, store.reserve("o1", "SKU", 2));
        assertEquals(Reservation.REJECTED, store.reserve("o2", "SKU", 2));
        assertEquals(1, store.available("SKU"));
        assertEquals(Reservation.REJECTED, store.reserve("o3", "NOPE", 1));
    }

    @Test
    void aRedeliveredOrderDoesNotReserveTwice() {
        store.add("SKU", 5);
        assertEquals(Reservation.RESERVED, store.reserve("o1", "SKU", 2));
        assertEquals(Reservation.RESERVED, store.reserve("o1", "SKU", 2));
        assertEquals(3, store.available("SKU"));
    }

    @Test
    void aRedeliveredRejectionStaysRejectedEvenIfStockArrivedSince() {
        assertEquals(Reservation.REJECTED, store.reserve("o1", "SKU", 1));
        store.add("SKU", 5);
        assertEquals(Reservation.REJECTED, store.reserve("o1", "SKU", 1));
        assertEquals(5, store.available("SKU"));
    }

    @Test
    void releaseReturnsStockExactlyOnce() {
        store.add("SKU", 5);
        store.reserve("o1", "SKU", 2);
        assertTrue(store.release("o1"));
        assertEquals(5, store.available("SKU"));
        assertFalse(store.release("o1"));
        assertEquals(5, store.available("SKU"));
        assertEquals(Reservation.RELEASED, store.reserve("o1", "SKU", 2));
        assertEquals(5, store.available("SKU"));
    }

    @Test
    void releasingARejectedOrUnknownOrderChangesNothing() {
        store.add("SKU", 1);
        store.reserve("o1", "SKU", 5);
        assertFalse(store.release("o1"));
        assertFalse(store.release("unknown"));
        assertEquals(1, store.available("SKU"));
    }

    @Test
    void concurrentOrdersNeverOversell() throws Exception {
        store.add("SKU", 100);
        List<Future<Reservation>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            for (int i = 0; i < 300; i++) {
                String id = "o" + i;
                results.add(pool.submit(() -> store.reserve(id, "SKU", 1)));
            }
            int reserved = 0;
            for (Future<Reservation> result : results) {
                if (result.get() == Reservation.RESERVED) {
                    reserved++;
                }
            }
            assertEquals(100, reserved);
        }
        assertEquals(0, store.available("SKU"));
    }
}
