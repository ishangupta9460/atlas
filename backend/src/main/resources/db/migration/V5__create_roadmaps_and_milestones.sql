-- DOM-002: structural Roadmap and Milestone entities.  A Goal has at most
-- one Roadmap; milestones use display_order because ORDER is SQL-reserved.

CREATE TABLE roadmaps (
    id         BIGINT       AUTO_INCREMENT PRIMARY KEY,
    goal_id    BIGINT       NOT NULL,
    source     VARCHAR(32)  NOT NULL,
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT fk_roadmaps_goal FOREIGN KEY (goal_id) REFERENCES goals(id),
    CONSTRAINT uq_roadmaps_goal UNIQUE (goal_id),
    CONSTRAINT chk_roadmaps_source CHECK (source IN ('user_interview', 'imported'))
);

CREATE INDEX idx_roadmaps_goal_id ON roadmaps(goal_id);

CREATE TABLE milestones (
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY,
    roadmap_id    BIGINT       NOT NULL,
    title         VARCHAR(255) NOT NULL,
    display_order INT          NOT NULL,

    CONSTRAINT fk_milestones_roadmap FOREIGN KEY (roadmap_id) REFERENCES roadmaps(id)
);

CREATE INDEX idx_milestones_roadmap_order ON milestones(roadmap_id, display_order);
