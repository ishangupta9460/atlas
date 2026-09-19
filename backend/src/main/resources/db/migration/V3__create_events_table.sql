-- FOUND-003: minimal walking-skeleton event storage.
-- The full Event Log schema is intentionally deferred to EVT-001.

CREATE TABLE events (
    id        BIGINT       AUTO_INCREMENT PRIMARY KEY,
    task_id   BIGINT       NOT NULL,
    type      VARCHAR(32)  NOT NULL,
    timestamp DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT fk_events_task FOREIGN KEY (task_id) REFERENCES tasks(id),
    CONSTRAINT chk_events_type CHECK (type IN ('task.created', 'task.started', 'task.finished'))
);
