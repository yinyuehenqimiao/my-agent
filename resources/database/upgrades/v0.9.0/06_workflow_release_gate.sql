CREATE TABLE IF NOT EXISTS wf_simulation_report (
  id BIGSERIAL PRIMARY KEY, version_id BIGINT NOT NULL, content_hash VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL, report TEXT, created_by VARCHAR(64) NOT NULL,
  created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_wf_simulation_version ON wf_simulation_report(version_id,created_time);
