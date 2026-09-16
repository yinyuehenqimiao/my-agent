-- 组织主数据：部门与员工。与 myagent 库隔离，智能体不得直接改这些表。

CREATE TABLE erp_department (
    tenant_id VARCHAR(64) NOT NULL,
    department_id VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    parent_id VARCHAR(64),
    PRIMARY KEY (tenant_id, department_id)
);
COMMENT ON TABLE erp_department IS '部门；按租户隔离';
COMMENT ON COLUMN erp_department.tenant_id IS '租户 ID';
COMMENT ON COLUMN erp_department.department_id IS '部门 ID';
COMMENT ON COLUMN erp_department.name IS '部门名称';
COMMENT ON COLUMN erp_department.parent_id IS '上级部门 ID，根部门为空';

CREATE TABLE erp_employee (
    tenant_id VARCHAR(64) NOT NULL,
    employee_id VARCHAR(64) NOT NULL,
    employee_no VARCHAR(64) NOT NULL,
    full_name VARCHAR(128) NOT NULL,
    hire_date DATE NOT NULL,
    status VARCHAR(16) NOT NULL,
    department_id VARCHAR(64) NOT NULL,
    position_name VARCHAR(128),
    manager_id VARCHAR(64),
    business_role VARCHAR(32) NOT NULL,
    PRIMARY KEY (tenant_id, employee_id),
    UNIQUE (tenant_id, employee_no),
    FOREIGN KEY (tenant_id, department_id) REFERENCES erp_department(tenant_id, department_id)
);
COMMENT ON TABLE erp_employee IS '员工主数据';
COMMENT ON COLUMN erp_employee.tenant_id IS '租户 ID';
COMMENT ON COLUMN erp_employee.employee_id IS '员工 ID，通常与智能体 user 对齐';
COMMENT ON COLUMN erp_employee.employee_no IS '工号，租户内唯一';
COMMENT ON COLUMN erp_employee.full_name IS '姓名';
COMMENT ON COLUMN erp_employee.hire_date IS '入职日期，用于司龄';
COMMENT ON COLUMN erp_employee.status IS '在职状态，如 ACTIVE';
COMMENT ON COLUMN erp_employee.department_id IS '所属部门';
COMMENT ON COLUMN erp_employee.position_name IS '岗位名称';
COMMENT ON COLUMN erp_employee.manager_id IS '直属主管员工 ID';
COMMENT ON COLUMN erp_employee.business_role IS 'EMPLOYEE/MANAGER/HR';

CREATE TABLE erp_employee_permission (
    tenant_id VARCHAR(64) NOT NULL,
    employee_id VARCHAR(64) NOT NULL,
    permission_code VARCHAR(128) NOT NULL,
    permission_name VARCHAR(128) NOT NULL,
    PRIMARY KEY (tenant_id, employee_id, permission_code),
    FOREIGN KEY (tenant_id, employee_id) REFERENCES erp_employee(tenant_id, employee_id)
);
COMMENT ON TABLE erp_employee_permission IS '员工可执行的 ERP 功能权限';
COMMENT ON COLUMN erp_employee_permission.tenant_id IS '租户 ID';
COMMENT ON COLUMN erp_employee_permission.employee_id IS '员工 ID';
COMMENT ON COLUMN erp_employee_permission.permission_code IS '权限码';
COMMENT ON COLUMN erp_employee_permission.permission_name IS '权限名称';
