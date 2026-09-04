ALTER TABLE cleanup_task
  ADD COLUMN next_attempt_at TIMESTAMP(6) NULL,
  ADD COLUMN locked_at TIMESTAMP(6) NULL;

UPDATE cleanup_task
SET next_attempt_at = created_at
WHERE next_attempt_at IS NULL;

CREATE INDEX idx_cleanup_task_claim ON cleanup_task (status, next_attempt_at);
