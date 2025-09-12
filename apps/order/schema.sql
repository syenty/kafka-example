CREATE TABLE orders
(
    id            BIGSERIAL PRIMARY KEY,
    inventory_id  VARCHAR(255) NOT NULL,
    quantity      INT          NOT NULL,
    status        VARCHAR(50)  NOT NULL DEFAULT 'PENDING_PAYMENT' -- e.g., 'PENDING_PAYMENT', 'PAID', 'CANCELLED'
);