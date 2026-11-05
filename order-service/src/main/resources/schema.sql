CREATE TABLE IF NOT EXISTS orders (
    id           VARCHAR(36) PRIMARY KEY,
    sku          VARCHAR(128) NOT NULL,
    qty          INTEGER NOT NULL,
    amount_cents BIGINT NOT NULL,
    customer_id  VARCHAR(128),
    status       VARCHAR(32) NOT NULL
);
