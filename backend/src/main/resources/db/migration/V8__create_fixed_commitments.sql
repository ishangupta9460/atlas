-- DOM-006: user-owned fixed reservations. Overlap and recurrence interpretation
-- belong to later Scheduling/Recovery stories, not persistence.
CREATE TABLE fixed_commitments (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    title           VARCHAR(255) NOT NULL,
    start_time      DATETIME(6) NOT NULL,
    end_time        DATETIME(6) NOT NULL,
    source          VARCHAR(32) NOT NULL,
    recurrence_rule TEXT NULL,
    CONSTRAINT fk_fixed_commitments_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT chk_fixed_commitments_source CHECK (source IN ('manual', 'screenshot_import')),
    CONSTRAINT chk_fixed_commitments_interval CHECK (end_time > start_time)
);

CREATE INDEX idx_fixed_commitments_user_time ON fixed_commitments(user_id, start_time, end_time);
