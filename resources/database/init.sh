#!/usr/bin/env bash
# 初始化独立数据库 myagent
set -euo pipefail
CONTAINER="${1:-postgres}"
PGUSER="${2:-postgres}"
DBNAME="${3:-myagent}"
DIR="$(cd "$(dirname "$0")" && pwd)"

echo "==> create database $DBNAME"
docker exec -i "$CONTAINER" psql -U "$PGUSER" -d postgres -v ON_ERROR_STOP=1 -c \
  "SELECT 'CREATE DATABASE $DBNAME' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '$DBNAME')\gexec"

echo "==> schema"
docker exec -i "$CONTAINER" psql -U "$PGUSER" -d "$DBNAME" -v ON_ERROR_STOP=1 < "$DIR/01_schema.sql"
docker exec -i "$CONTAINER" psql -U "$PGUSER" -d "$DBNAME" -v ON_ERROR_STOP=1 < "$DIR/02_init_data.sql"
echo "done. users seeded on app startup: admin/admin123, demo/user123"
