CREATE TABLE prototype_owner_rel (
  prototype_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  PRIMARY KEY (prototype_id, user_id),
  CONSTRAINT fk_prototype_owner_rel_proto FOREIGN KEY (prototype_id) REFERENCES prototype(id),
  CONSTRAINT fk_prototype_owner_rel_user FOREIGN KEY (user_id) REFERENCES sys_user(id),
  INDEX idx_prototype_owner_rel_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO prototype_owner_rel (prototype_id, user_id)
SELECT id, owner_id FROM prototype WHERE owner_id IS NOT NULL;
