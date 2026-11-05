package com.shop.order.domain;

import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.Optional;

public class JdbcOrderRepository implements OrderRepository {
    private final JdbcClient jdbc;

    public JdbcOrderRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(Order order) {
        jdbc.sql("INSERT INTO orders (id, sku, qty, amount_cents, customer_id, status) VALUES (?, ?, ?, ?, ?, ?)")
                .params(order.id(), order.sku(), order.qty(), order.amountCents(), order.customerId(), order.status().name())
                .update();
    }

    @Override
    public Optional<Order> find(String id) {
        return jdbc.sql("SELECT id, sku, qty, amount_cents, customer_id, status FROM orders WHERE id = ?")
                .param(id)
                .query((rs, row) -> new Order(
                        rs.getString("id"),
                        rs.getString("sku"),
                        rs.getInt("qty"),
                        rs.getLong("amount_cents"),
                        rs.getString("customer_id"),
                        OrderStatus.valueOf(rs.getString("status"))))
                .optional();
    }

    @Override
    public boolean transition(String id, OrderStatus from, OrderStatus to) {
        return jdbc.sql("UPDATE orders SET status = ? WHERE id = ? AND status = ?")
                .params(to.name(), id, from.name())
                .update() == 1;
    }
}
