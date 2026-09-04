CREATE TABLE prototype_version (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  public_id CHAR(26) NOT NULL UNIQUE,
  prototype_id BIGINT NOT NULL,
  version_no INT NOT NULL,
  change_log VARCHAR(1000) NOT NULL,
  status VARCHAR(20) NOT NULL,
  source_type VARCHAR(8) NOT NULL,
  source_object_key VARCHAR(512) NOT NULL,
  publish_prefix VARCHAR(512),
  entry_path VARCHAR(512),
  file_count INT,
  source_size BIGINT NOT NULL,
  expanded_size BIGINT,
  checksum CHAR(64) NOT NULL,
  failure_stage VARCHAR(32),
  failure_message VARCHAR(1000),
  created_by BIGINT NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  published_at TIMESTAMP(6),
  UNIQUE KEY uk_prototype_version_no (prototype_id, version_no),
  FOREIGN KEY (prototype_id) REFERENCES prototype(id),
  FOREIGN KEY (created_by) REFERENCES sys_user(id)
);

ALTER TABLE prototype
  ADD CONSTRAINT fk_prototype_current_version
  FOREIGN KEY (current_version_id) REFERENCES prototype_version(id);

CREATE TABLE publish_job (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  prototype_id BIGINT NOT NULL,
  version_id BIGINT NOT NULL UNIQUE,
  status VARCHAR(20) NOT NULL,
  stage VARCHAR(32) NOT NULL,
  progress INT NOT NULL DEFAULT 0,
  attempt_count INT NOT NULL DEFAULT 0,
  active_guard BIGINT GENERATED ALWAYS AS (
    CASE WHEN status IN ('PENDING','RUNNING','RETRYING') THEN prototype_id ELSE NULL END
  ) STORED,
  locked_at TIMESTAMP(6),
  error_detail TEXT,
  created_at TIMESTAMP(6) NOT NULL,
  finished_at TIMESTAMP(6),
  UNIQUE KEY uk_one_active_job_per_prototype (active_guard),
  FOREIGN KEY (prototype_id) REFERENCES prototype(id),
  FOREIGN KEY (version_id) REFERENCES prototype_version(id)
);

CREATE TABLE temporary_upload (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  public_id CHAR(26) NOT NULL UNIQUE,
  user_id BIGINT NOT NULL,
  filename VARCHAR(255) NOT NULL,
  file_type VARCHAR(8) NOT NULL,
  object_key VARCHAR(512) NOT NULL,
  claimed_size BIGINT NOT NULL,
  actual_size BIGINT,
  checksum CHAR(64),
  status VARCHAR(20) NOT NULL,
  expires_at TIMESTAMP(6) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  FOREIGN KEY (user_id) REFERENCES sys_user(id)
);
