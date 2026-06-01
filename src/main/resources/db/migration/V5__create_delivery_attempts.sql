CREATE TABLE delivery_attempts (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    notification_job_id BIGINT NOT NULL,
    tenant_id           BIGINT NOT NULL,
    attempt_number      INT NOT NULL,
    status              ENUM('SUCCESS','FAILURE') NOT NULL,
    provider_response   TEXT,
    attempted_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_attempt_job FOREIGN KEY (notification_job_id) REFERENCES notification_jobs(id),
    INDEX idx_attempt_job (notification_job_id)
);
