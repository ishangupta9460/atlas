-- DOM-004: Recurring Intentions have a mutable per-week remaining count.
-- category_id is intentionally a plain nullable column until DOM-005 owns categories.
CREATE TABLE recurring_intentions (
    id                           BIGINT       AUTO_INCREMENT PRIMARY KEY,
    user_id                      BIGINT       NOT NULL,
    goal_id                      BIGINT       NULL,
    title                        VARCHAR(255) NOT NULL,
    target_count_per_week        INT          NOT NULL,
    current_week_remaining_count INT          NOT NULL,
    category_id                  BIGINT       NULL,
    flexibility_tier             VARCHAR(16)  NOT NULL,
    created_at                   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT fk_recurring_intentions_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_recurring_intentions_goal FOREIGN KEY (goal_id) REFERENCES goals(id),
    CONSTRAINT chk_recurring_intentions_target_positive CHECK (target_count_per_week > 0),
    CONSTRAINT chk_recurring_intentions_remaining_nonnegative CHECK (current_week_remaining_count >= 0),
    CONSTRAINT chk_recurring_intentions_flexibility CHECK (flexibility_tier IN ('fixed', 'protected', 'flexible', 'optional'))
);

CREATE INDEX idx_recurring_intentions_user_id ON recurring_intentions(user_id);
CREATE INDEX idx_recurring_intentions_goal_id ON recurring_intentions(goal_id);
