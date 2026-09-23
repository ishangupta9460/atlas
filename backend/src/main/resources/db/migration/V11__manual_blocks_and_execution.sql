-- User-directed placements; autonomous scheduling remains a separate capability.
CREATE TABLE scheduled_blocks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    commitment_id BIGINT NULL,
    recurring_intention_id BIGINT NULL,
    superseded_by_block_id BIGINT NULL,
    start_time DATETIME(6) NOT NULL,
    end_time DATETIME(6) NOT NULL,
    state VARCHAR(20) NOT NULL DEFAULT 'scheduled',
    user_moved_flag BOOLEAN NOT NULL DEFAULT TRUE,
    placement_reason TEXT NOT NULL,
    CONSTRAINT fk_blocks_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_blocks_commitment FOREIGN KEY (commitment_id) REFERENCES commitments(id),
    CONSTRAINT fk_blocks_recurring FOREIGN KEY (recurring_intention_id) REFERENCES recurring_intentions(id),
    CONSTRAINT fk_blocks_replacement FOREIGN KEY (superseded_by_block_id) REFERENCES scheduled_blocks(id),
    CONSTRAINT chk_blocks_source CHECK ((commitment_id IS NOT NULL AND recurring_intention_id IS NULL) OR (commitment_id IS NULL AND recurring_intention_id IS NOT NULL)),
    CONSTRAINT chk_blocks_window CHECK (end_time > start_time),
    CONSTRAINT chk_blocks_state CHECK (state IN ('scheduled','active','completed','superseded'))
);
CREATE INDEX idx_blocks_owner_time ON scheduled_blocks(user_id, start_time);

-- Mutable runtime, separate from the immutable report inserted at Finish.
CREATE TABLE focus_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    scheduled_block_id BIGINT NOT NULL UNIQUE,
    state VARCHAR(16) NOT NULL,
    actual_start DATETIME(6) NOT NULL,
    running_since DATETIME(6) NULL,
    active_millis BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_focus_block FOREIGN KEY (scheduled_block_id) REFERENCES scheduled_blocks(id),
    CONSTRAINT chk_focus_state CHECK (state IN ('running','paused','finished')),
    CONSTRAINT chk_focus_elapsed CHECK (active_millis >= 0)
);
CREATE TABLE actual_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    scheduled_block_id BIGINT NOT NULL UNIQUE,
    actual_start DATETIME(6) NOT NULL,
    actual_end DATETIME(6) NOT NULL,
    active_millis BIGINT NOT NULL,
    user_reported_outcome TEXT NOT NULL,
    notes TEXT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completion_pct DECIMAL(5,2) NOT NULL,
    CONSTRAINT fk_actual_block FOREIGN KEY (scheduled_block_id) REFERENCES scheduled_blocks(id),
    CONSTRAINT chk_actual_progress CHECK (completion_pct BETWEEN 0 AND 100),
    CONSTRAINT chk_actual_time CHECK (actual_end >= actual_start AND active_millis >= 0)
);
CREATE TABLE execution_idempotency (
    user_id BIGINT NOT NULL,
    request_key VARCHAR(100) NOT NULL,
    fingerprint TEXT NOT NULL,
    response_json TEXT NOT NULL,
    PRIMARY KEY (user_id, request_key),
    CONSTRAINT fk_execution_key_user FOREIGN KEY (user_id) REFERENCES users(id)
);
