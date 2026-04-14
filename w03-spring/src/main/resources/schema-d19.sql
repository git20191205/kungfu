-- D19 Transaction Demo 数据表
DROP TABLE IF EXISTS account;

CREATE TABLE account (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    balance DECIMAL(10, 2) NOT NULL DEFAULT 0
);

INSERT INTO account (name, balance) VALUES ('Alice', 1000.00);
INSERT INTO account (name, balance) VALUES ('Bob', 1000.00);
