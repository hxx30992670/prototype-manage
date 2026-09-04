CREATE TABLE sys_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  public_id CHAR(26) NOT NULL UNIQUE,
  username VARCHAR(50) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  display_name VARCHAR(100) NOT NULL,
  department VARCHAR(100),
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  auth_provider VARCHAR(20) NOT NULL DEFAULT 'LOCAL',
  external_subject VARCHAR(255),
  must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
  last_login_at TIMESTAMP NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE sys_role (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(50) NOT NULL UNIQUE,
  name VARCHAR(100) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE sys_user_role (
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  PRIMARY KEY (user_id, role_id),
  CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
  CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES sys_role(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE prototype_category (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(50) NOT NULL UNIQUE,
  name VARCHAR(100) NOT NULL,
  sort_no INT NOT NULL DEFAULT 0,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE prototype_tag (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(50) NOT NULL UNIQUE,
  color VARCHAR(30),
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE prototype (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  public_id CHAR(26) NOT NULL UNIQUE,
  code VARCHAR(50) NOT NULL UNIQUE,
  name VARCHAR(100) NOT NULL,
  description VARCHAR(500),
  public_summary VARCHAR(2000),
  category_id BIGINT NOT NULL,
  created_by BIGINT NOT NULL,
  owner_id BIGINT NOT NULL,
  visibility VARCHAR(32) NOT NULL DEFAULT 'ALL_INTERNAL',
  review_status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
  current_version_id BIGINT NULL,
  cover_object_key VARCHAR(512),
  archived BOOLEAN NOT NULL DEFAULT FALSE,
  deleted_at TIMESTAMP NULL,
  row_version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT fk_prototype_creator FOREIGN KEY (created_by) REFERENCES sys_user(id),
  CONSTRAINT fk_prototype_owner FOREIGN KEY (owner_id) REFERENCES sys_user(id),
  CONSTRAINT fk_prototype_category FOREIGN KEY (category_id) REFERENCES prototype_category(id),
  INDEX idx_prototype_owner (owner_id),
  INDEX idx_prototype_category (category_id),
  INDEX idx_prototype_review_status (review_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE prototype_tag_rel (
  prototype_id BIGINT NOT NULL,
  tag_id BIGINT NOT NULL,
  PRIMARY KEY (prototype_id, tag_id),
  CONSTRAINT fk_prototype_tag_prototype FOREIGN KEY (prototype_id) REFERENCES prototype(id) ON DELETE CASCADE,
  CONSTRAINT fk_prototype_tag_tag FOREIGN KEY (tag_id) REFERENCES prototype_tag(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE audit_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  trace_id VARCHAR(64) NOT NULL,
  actor_type VARCHAR(20) NOT NULL,
  actor_id BIGINT NULL,
  action VARCHAR(64) NOT NULL,
  target_type VARCHAR(32) NOT NULL,
  target_id VARCHAR(64) NOT NULL,
  result VARCHAR(16) NOT NULL,
  ip VARCHAR(64),
  user_agent VARCHAR(512),
  summary VARCHAR(1000),
  created_at TIMESTAMP(6) NOT NULL,
  INDEX idx_audit_query (created_at, action, actor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
