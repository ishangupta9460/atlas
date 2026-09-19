-- DOM-003: permanent work foundation; legacy tasks and events remain untouched.
ALTER TABLE categories ADD COLUMN default_importance VARCHAR(16) NULL;
ALTER TABLE categories ADD CONSTRAINT chk_categories_importance
    CHECK (default_importance IN ('low', 'medium', 'high', 'critical'));

CREATE TABLE commitments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    milestone_id BIGINT NULL,
    goal_id BIGINT NULL,
    category_id BIGINT NULL,
    title VARCHAR(255) NULL,
    description TEXT NULL,
    completion_criterion TEXT NULL,
    own_deadline DATETIME(6) NULL,
    is_hard_consequence BOOLEAN NOT NULL DEFAULT FALSE,
    importance VARCHAR(16) NOT NULL,
    flexibility_tier VARCHAR(16) NOT NULL,
    work_state VARCHAR(32) NOT NULL,
    user_moved_flag BOOLEAN NOT NULL DEFAULT FALSE,
    current_completion_pct DECIMAL(5,2) NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_commitments_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_commitments_goal FOREIGN KEY (goal_id) REFERENCES goals(id),
    CONSTRAINT fk_commitments_milestone FOREIGN KEY (milestone_id) REFERENCES milestones(id),
    CONSTRAINT fk_commitments_category FOREIGN KEY (category_id) REFERENCES categories(id),
    CONSTRAINT chk_commitments_state CHECK (work_state IN ('draft','ready','deferred','in_progress','completed','cancelled')),
    CONSTRAINT chk_commitments_importance CHECK (importance IN ('low','medium','high','critical')),
    CONSTRAINT chk_commitments_flexibility CHECK (flexibility_tier IN ('fixed','protected','flexible','optional')),
    CONSTRAINT chk_commitments_progress CHECK (current_completion_pct BETWEEN 0 AND 100),
    CONSTRAINT chk_commitments_ready_definition CHECK (work_state NOT IN ('ready','in_progress','completed') OR
        (title IS NOT NULL AND CHAR_LENGTH(TRIM(title)) > 0 AND completion_criterion IS NOT NULL AND CHAR_LENGTH(TRIM(completion_criterion)) > 0)),
    CONSTRAINT chk_commitments_milestone_goal CHECK (milestone_id IS NULL OR goal_id IS NOT NULL)
);
CREATE INDEX idx_commitments_goal_state ON commitments(goal_id, work_state);
CREATE INDEX idx_commitments_owner_state ON commitments(user_id, work_state, id);
