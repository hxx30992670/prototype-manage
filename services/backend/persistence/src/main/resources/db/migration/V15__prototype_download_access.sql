ALTER TABLE prototype
  ADD COLUMN download_access VARCHAR(32) NOT NULL DEFAULT 'MANAGERS_ONLY';

CREATE TABLE prototype_downloader_rel (
  prototype_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  PRIMARY KEY (prototype_id, user_id),
  CONSTRAINT fk_prototype_downloader_rel_proto FOREIGN KEY (prototype_id) REFERENCES prototype(id),
  CONSTRAINT fk_prototype_downloader_rel_user FOREIGN KEY (user_id) REFERENCES sys_user(id),
  INDEX idx_prototype_downloader_rel_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
