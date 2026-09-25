-- Local weekly availability is distinct from the absolute UTC reservations in V8/V11.
CREATE TABLE scheduling_config (
    user_id BIGINT PRIMARY KEY,
    timezone VARCHAR(100) NULL,
    workable_fraction DECIMAL(5,4) NOT NULL DEFAULT 0.7000,
    buffer_minutes INT NOT NULL DEFAULT 10,
    continuous_work_minutes INT NOT NULL DEFAULT 50,
    break_minutes INT NOT NULL DEFAULT 10,
    CONSTRAINT fk_scheduling_config_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT chk_capacity_fraction CHECK (workable_fraction >= 0 AND workable_fraction <= 1),
    CONSTRAINT chk_capacity_buffer CHECK (buffer_minutes BETWEEN 0 AND 60),
    CONSTRAINT chk_capacity_continuous CHECK (continuous_work_minutes BETWEEN 1 AND 240),
    CONSTRAINT chk_capacity_break CHECK (break_minutes BETWEEN 1 AND 60)
);

CREATE TABLE working_hours_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    day_of_week INT NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    kind VARCHAR(16) NOT NULL,
    CONSTRAINT fk_working_hours_config FOREIGN KEY (user_id) REFERENCES scheduling_config(user_id),
    CONSTRAINT chk_working_hours_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT chk_working_hours_kind CHECK (kind IN ('working','sleep','protected')),
    CONSTRAINT chk_working_hours_duration CHECK (start_time <> end_time),
    CONSTRAINT uq_working_hours UNIQUE (user_id, day_of_week, start_time, end_time, kind)
);
