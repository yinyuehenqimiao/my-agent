-- Additive migration; existing knowledge bases remain administrator-only until published.
CREATE TABLE IF NOT EXISTS ent_employee (
 user_id VARCHAR(64) PRIMARY KEY, display_name VARCHAR(100) NOT NULL,
 department VARCHAR(100) NOT NULL, business_role VARCHAR(20) NOT NULL CHECK (business_role IN ('EMPLOYEE','MANAGER','HR')),
 manager_id VARCHAR(64), hr_id VARCHAR(64), active BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE IF NOT EXISTS ent_leave_account (
 user_id VARCHAR(64) NOT NULL REFERENCES ent_employee(user_id), year INTEGER NOT NULL,
 total INTEGER NOT NULL DEFAULT 10, frozen INTEGER NOT NULL DEFAULT 0, used INTEGER NOT NULL DEFAULT 0,
 PRIMARY KEY(user_id,year), CHECK(total>=0 AND frozen>=0 AND used>=0 AND frozen+used<=total)
);
CREATE TABLE IF NOT EXISTS ent_workday (day DATE PRIMARY KEY, working BOOLEAN NOT NULL, description VARCHAR(200));
CREATE TABLE IF NOT EXISTS ent_leave_request (
 id VARCHAR(64) PRIMARY KEY, user_id VARCHAR(64) NOT NULL REFERENCES ent_employee(user_id),
 leave_type VARCHAR(20) NOT NULL CHECK(leave_type IN ('ANNUAL','PERSONAL')),
 start_date DATE NOT NULL, end_date DATE NOT NULL, days INTEGER NOT NULL CHECK(days>0),
 reason VARCHAR(1000) NOT NULL, status VARCHAR(30) NOT NULL,
 manager_id VARCHAR(64) NOT NULL, hr_id VARCHAR(64) NOT NULL,
 created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL,
 CHECK(start_date<=end_date), CHECK(user_id<>manager_id AND user_id<>hr_id AND manager_id<>hr_id),
 CHECK(status IN ('DRAFT','PENDING_MANAGER','PENDING_HR','APPROVED','REJECTED','WITHDRAWN'))
);
CREATE INDEX IF NOT EXISTS ent_leave_owner_idx ON ent_leave_request(user_id,created_at);
CREATE TABLE IF NOT EXISTS ent_approval_task (
 id VARCHAR(64) PRIMARY KEY, request_id VARCHAR(64) NOT NULL REFERENCES ent_leave_request(id),
 assignee_id VARCHAR(64) NOT NULL, stage VARCHAR(20) NOT NULL, status VARCHAR(20) NOT NULL,
 UNIQUE(request_id,stage)
);
CREATE INDEX IF NOT EXISTS ent_approval_assignee_idx ON ent_approval_task(assignee_id,status);
CREATE TABLE IF NOT EXISTS ent_action (
 id VARCHAR(64) PRIMARY KEY, user_id VARCHAR(64) NOT NULL, conversation_id VARCHAR(64) NOT NULL,
 kind VARCHAR(20) NOT NULL, request_id VARCHAR(64) NOT NULL REFERENCES ent_leave_request(id),
 payload TEXT NOT NULL, status VARCHAR(20) NOT NULL, result TEXT,
 expires_at TIMESTAMP NOT NULL, created_at TIMESTAMP NOT NULL,
 dedup_key VARCHAR(64) NOT NULL, UNIQUE(user_id,conversation_id,dedup_key)
);
CREATE INDEX IF NOT EXISTS ent_action_session_idx ON ent_action(user_id,conversation_id);
CREATE TABLE IF NOT EXISTS ent_audit (
 id VARCHAR(64) PRIMARY KEY, request_id VARCHAR(64) NOT NULL REFERENCES ent_leave_request(id),
 actor_id VARCHAR(64) NOT NULL, action_id VARCHAR(64), event VARCHAR(40) NOT NULL,
 comment VARCHAR(1000), created_at TIMESTAMP NOT NULL
);
CREATE TABLE IF NOT EXISTS ent_kb_grant (
 kb_id VARCHAR(64) NOT NULL, audience VARCHAR(20) NOT NULL CHECK(audience IN ('ALL','DEPARTMENT','ROLE')),
 subject VARCHAR(100) NOT NULL, PRIMARY KEY(kb_id,audience,subject)
);
CREATE TABLE IF NOT EXISTS ent_document_policy (
 doc_id VARCHAR(64) PRIMARY KEY, version VARCHAR(64) NOT NULL, effective_date DATE NOT NULL
);
