CREATE TABLE payments
(
    id       BIGSERIAL PRIMARY KEY,
    order_id BIGINT       NOT NULL,
    status   VARCHAR(50)  NOT NULL,
    reason   VARCHAR(255)
);