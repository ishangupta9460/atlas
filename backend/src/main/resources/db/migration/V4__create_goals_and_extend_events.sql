-- DOM-001: permanent Goal domain model and the generic Event Log fields it needs.
-- The Phase 0 task event rows remain valid: task_id remains populated for them,
-- while entity_type/entity_id provide the common event-log identity.

CREATE TABLE goals (
    id              BIGINT       AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    title           VARCHAR(255) NOT NULL,
    description     TEXT         NULL,
    target_deadline DATE         NULL,
    lifecycle_state VARCHAR(32)  NOT NULL,
    planning_state  VARCHAR(32)  NOT NULL,
    created_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT fk_goals_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT chk_goals_lifecycle_state CHECK (lifecycle_state IN ('active', 'completed', 'abandoned')),
    CONSTRAINT chk_goals_planning_state CHECK (planning_state IN ('active', 'deferred', 'paused', 'at_risk'))
);

CREATE INDEX idx_goals_user_id ON goals(user_id);

-- V3 intentionally limited events to the walking-skeleton task event types.
-- Promote it in place so there is one append-only Event Log for both old task
-- history and all subsequent domain state changes.
ALTER TABLE events DROP CONSTRAINT chk_events_type;
ALTER TABLE events MODIFY task_id BIGINT NULL;
ALTER TABLE events MODIFY type VARCHAR(64) NOT NULL;
ALTER TABLE events ADD COLUMN entity_type VARCHAR(64) NULL;
ALTER TABLE events ADD COLUMN entity_id BIGINT NULL;
ALTER TABLE events ADD COLUMN actor VARCHAR(16) NOT NULL DEFAULT 'user';
ALTER TABLE events ADD COLUMN reason TEXT NULL;
ALTER TABLE events ADD COLUMN payload TEXT NULL;
UPDATE events SET entity_type = 'task', entity_id = task_id WHERE entity_type IS NULL;
ALTER TABLE events MODIFY entity_type VARCHAR(64) NOT NULL;
ALTER TABLE events MODIFY entity_id BIGINT NOT NULL;
CREATE INDEX idx_events_entity_timestamp ON events(entity_type, entity_id, timestamp);
