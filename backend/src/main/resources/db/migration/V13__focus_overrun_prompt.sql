-- Runtime delivery marker only; finished execution facts remain unchanged.
ALTER TABLE focus_sessions ADD COLUMN overrun_prompted_at DATETIME(6) NULL;
