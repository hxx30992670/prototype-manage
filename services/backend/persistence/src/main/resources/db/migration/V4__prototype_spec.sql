CREATE TABLE prototype_spec (
  prototype_id BIGINT PRIMARY KEY,
  goal TEXT,
  core_flow TEXT,
  interaction_rules TEXT,
  business_constraints TEXT,
  data_requirements TEXT,
  acceptance_notes TEXT,
  markdown_extra MEDIUMTEXT,
  row_version BIGINT NOT NULL DEFAULT 0,
  updated_by BIGINT NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  FOREIGN KEY (prototype_id) REFERENCES prototype(id)
);
