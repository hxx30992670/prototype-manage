CREATE TABLE system_config (
  config_key VARCHAR(128) PRIMARY KEY,
  config_value VARCHAR(2000) NOT NULL,
  value_type VARCHAR(16) NOT NULL,
  updated_by BIGINT NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
