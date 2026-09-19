-- FOUND-003: minimal walking-skeleton task storage.
-- This deliberately precedes the full Commitment domain model (DOM-003).

CREATE TABLE tasks (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    title       VARCHAR(255) NOT NULL,
    status      VARCHAR(32)  NOT NULL,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    started_at  DATETIME(6)  NULL,
    finished_at DATETIME(6)  NULL,

    CONSTRAINT fk_tasks_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT chk_tasks_status CHECK (status IN ('ready', 'in_progress', 'completed'))
);
