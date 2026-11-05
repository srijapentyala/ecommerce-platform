package com.shop.inventory.domain;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Stock lives in the database so that every replica sees the same count. The conditional
 * UPDATE is what prevents overselling: the database applies it atomically, so two replicas
 * racing for the last unit cannot both succeed.
 */
public class JdbcStockStore implements StockStore {
    private final JdbcClient jdbc;
    private final TransactionTemplate tx;

    public JdbcStockStore(JdbcClient jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    @Override
    public int add(String sku, int qty) {
        return tx.execute(status -> {
            jdbc.sql("INSERT INTO stock (sku, qty) VALUES (?, 0) ON CONFLICT DO NOTHING").param(sku).update();
            jdbc.sql("UPDATE stock SET qty = qty + ? WHERE sku = ?").params(qty, sku).update();
            return available(sku);
        });
    }

    @Override
    public int available(String sku) {
        return jdbc.sql("SELECT qty FROM stock WHERE sku = ?").param(sku).query(Integer.class).optional().orElse(0);
    }

    @Override
    public Reservation reserve(String orderId, String sku, int qty) {
        return tx.execute(status -> {
            int claimed = jdbc.sql("INSERT INTO reservations (order_id, sku, qty, state) VALUES (?, ?, ?, 'PENDING') "
                            + "ON CONFLICT DO NOTHING")
                    .params(orderId, sku, qty)
                    .update();
            if (claimed == 0) {
                return Reservation.valueOf(jdbc.sql("SELECT state FROM reservations WHERE order_id = ?")
                        .param(orderId).query(String.class).single());
            }
            boolean taken = jdbc.sql("UPDATE stock SET qty = qty - ? WHERE sku = ? AND qty >= ?")
                    .params(qty, sku, qty).update() == 1;
            Reservation outcome = taken ? Reservation.RESERVED : Reservation.REJECTED;
            jdbc.sql("UPDATE reservations SET state = ? WHERE order_id = ?").params(outcome.name(), orderId).update();
            return outcome;
        });
    }

    @Override
    public boolean release(String orderId) {
        return Boolean.TRUE.equals(tx.execute(status -> {
            boolean released = jdbc.sql("UPDATE reservations SET state = 'RELEASED' WHERE order_id = ? AND state = 'RESERVED'")
                    .param(orderId).update() == 1;
            if (released) {
                jdbc.sql("UPDATE stock SET qty = qty + (SELECT qty FROM reservations WHERE order_id = ?) "
                                + "WHERE sku = (SELECT sku FROM reservations WHERE order_id = ?)")
                        .params(orderId, orderId).update();
            }
            return released;
        }));
    }
}
