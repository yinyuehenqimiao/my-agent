#!/usr/bin/env bash
# 初始化独立数据库 myagent（Docker 容器名默认 postgres）
set -euo pipefail
CONTAINER="${1:-postgres}"
PGUSER="${2:-postgres}"
DBNAME="${3:-myagent}"
DIR="$(cd "$(dirname "$0")" && pwd)"

psql_file() {
  local file="$1"
  echo "  -> $file"
  docker exec -i "$CONTAINER" psql -U "$PGUSER" -d "$DBNAME" -v ON_ERROR_STOP=1 < "$DIR/$file"
}

echo "==> create database $DBNAME"
docker exec -i "$CONTAINER" psql -U "$PGUSER" -d postgres -v ON_ERROR_STOP=1 -c \
  "SELECT 'CREATE DATABASE $DBNAME' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '$DBNAME')\gexec"

echo "==> schema"
psql_file "01_schema.sql"
psql_file "02_init_data.sql"

echo "==> upgrades"
for file in \
  upgrades/v0.4.0/01_enterprise.sql \
  upgrades/v0.5.0/01_platform_rbac.sql \
  upgrades/v0.5.0/01_erp_pending_action.sql \
  upgrades/v0.5.0/02_platform_rbac_seed.sql \
  upgrades/v0.6.0/01_workflow_runtime.sql \
  upgrades/v0.6.0/01_mcp_catalog.sql \
  upgrades/v0.6.0/02_mcp_intent_binding.sql \
  upgrades/v0.6.0/02_agent_admin_permissions.sql \
  upgrades/v0.6.0/03_route_binding_admin.sql \
  upgrades/v0.6.0/04_unified_audit.sql \
  upgrades/v0.7.0/01_reasoning_trace.sql \
  upgrades/v0.7.0/02_employee_self_and_hr_policy_intents.sql \
  upgrades/v0.8.0/01_conversation_workflow_state.sql \
  upgrades/v0.8.0/02_capability_intent_examples.sql \
  upgrades/v0.9.0/01_workflow_process_source.sql \
  upgrades/v0.9.0/02_mcp_tool_contract.sql \
  upgrades/v0.9.0/03_workflow_version_package.sql \
  upgrades/v0.9.0/04_workflow_execution_state.sql \
  upgrades/v0.9.0/05_workflow_confirmation.sql \
  upgrades/v0.9.0/06_workflow_release_gate.sql \
  upgrades/v0.10.0/01_runtime_tool_catalog.sql \
  upgrades/v0.10.0/02_column_comments.sql
do
  psql_file "$file"
done

echo "done. users seeded on app startup: admin/admin, demo/user123, enterprise demo accounts with enterprise profile"
