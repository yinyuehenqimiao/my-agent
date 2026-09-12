-- myagent v0.5.0: Platform RBAC tables (Stage 1)
-- Additive migration; uses IF NOT EXISTS for repeatable execution.
-- Tenant scoping is reserved via tenant_id columns; current single-company uses 'default'.

CREATE TABLE IF NOT EXISTS sys_role (
    id           BIGSERIAL    PRIMARY KEY,
    role_code    VARCHAR(64)  NOT NULL,
    role_name    VARCHAR(128) NOT NULL,
    description  VARCHAR(512),
    enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    tenant_id    VARCHAR(64)  NOT NULL DEFAULT 'default',
    create_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sys_role_code UNIQUE (role_code)
);
COMMENT ON TABLE sys_role IS '平台角色（RBAC），与 t_user.role 业务角色分离';
COMMENT ON COLUMN sys_role.role_code IS '平台角色码：PLATFORM_ADMIN/WORKFLOW_EDITOR/...';

CREATE TABLE IF NOT EXISTS sys_permission (
    id           BIGSERIAL    PRIMARY KEY,
    perm_code    VARCHAR(64)  NOT NULL,
    perm_name    VARCHAR(128) NOT NULL,
    description  VARCHAR(512),
    category     VARCHAR(64)  NOT NULL,
    enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    create_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sys_perm_code UNIQUE (perm_code)
);
COMMENT ON TABLE sys_permission IS '平台权限码';
COMMENT ON COLUMN sys_permission.category IS '权限分组：workflow/tool/policy/audit/employee/knowledge';

CREATE TABLE IF NOT EXISTS sys_user_role (
    id           BIGSERIAL    PRIMARY KEY,
    user_id      BIGINT       NOT NULL,
    role_code    VARCHAR(64)  NOT NULL,
    tenant_id    VARCHAR(64)  NOT NULL DEFAULT 'default',
    create_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sys_user_role UNIQUE (user_id, role_code)
);
COMMENT ON TABLE sys_user_role IS '用户-平台角色关联';

CREATE TABLE IF NOT EXISTS sys_role_permission (
    id           BIGSERIAL    PRIMARY KEY,
    role_code    VARCHAR(64)  NOT NULL,
    perm_code    VARCHAR(64)  NOT NULL,
    create_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sys_role_perm UNIQUE (role_code, perm_code)
);
COMMENT ON TABLE sys_role_permission IS '平台角色-权限码关联';

CREATE TABLE IF NOT EXISTS sys_auth_audit (
    id           BIGSERIAL    PRIMARY KEY,
    user_id      VARCHAR(64),
    username     VARCHAR(128),
    tenant_id    VARCHAR(64)  NOT NULL DEFAULT 'default',
    perm_code    VARCHAR(64),
    action       VARCHAR(32)  NOT NULL,
    reason       VARCHAR(512),
    trace_id     VARCHAR(64),
    create_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE sys_auth_audit IS '鉴权审计记录，所有权限拒绝写入';
CREATE INDEX IF NOT EXISTS idx_sys_auth_audit_user ON sys_auth_audit (user_id);
CREATE INDEX IF NOT EXISTS idx_sys_auth_audit_perm ON sys_auth_audit (perm_code);
CREATE INDEX IF NOT EXISTS idx_sys_auth_audit_time ON sys_auth_audit (create_time);
