CREATE TABLE orders
(
    id            BIGSERIAL PRIMARY KEY,
    inventory_id  VARCHAR(255) NOT NULL,
    quantity      INT          NOT NULL
);