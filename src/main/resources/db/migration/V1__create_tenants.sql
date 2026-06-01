CREATE TABLE tenants (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    name                  VARCHAR(255) NOT NULL,
    slug                  VARCHAR(100) NOT NULL UNIQUE,
    monthly_campaign_limit INT NOT NULL DEFAULT 100,
    monthly_message_limit  INT NOT NULL DEFAULT 1000000,
    created_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO tenants (id, name, slug, monthly_campaign_limit, monthly_message_limit)
VALUES (1, 'Acme Corporation', 'acme', 100, 1000000),
       (2, 'Globex Corporation', 'globex', 200, 5000000);
