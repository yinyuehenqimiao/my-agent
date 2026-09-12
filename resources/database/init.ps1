# 初始化独立数据库 myagent（Docker 容器名默认 postgres）
param(
    [string]$Container = "postgres",
    [string]$PgUser = "postgres",
    [string]$DbName = "myagent"
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

Write-Host "==> 检查/创建数据库 $DbName"
$raw = docker exec $Container psql -U $PgUser -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='$DbName'" 2>$null
$exists = if ($null -eq $raw) { "" } else { "$raw".Trim() }
if ($exists -ne "1") {
    docker exec $Container psql -U $PgUser -d postgres -c "CREATE DATABASE $DbName"
    Write-Host "已创建 $DbName"
} else {
    Write-Host "数据库 $DbName 已存在"
}

Write-Host "==> 执行 schema"
Get-Content "$ScriptDir\01_schema.sql" -Raw | docker exec -i $Container psql -U $PgUser -d $DbName -v ON_ERROR_STOP=1
if ($LASTEXITCODE -ne 0) { throw "基础 schema 初始化失败" }

Write-Host "==> 执行 init_data（占位）"
Get-Content "$ScriptDir\02_init_data.sql" -Raw | docker exec -i $Container psql -U $PgUser -d $DbName -v ON_ERROR_STOP=1
if ($LASTEXITCODE -ne 0) { throw "基础数据初始化失败" }

Get-Content "$ScriptDir\upgrades\v0.4.0\01_enterprise.sql" -Raw -Encoding utf8 | docker exec -i $Container psql -U $PgUser -d $DbName -v ON_ERROR_STOP=1
if ($LASTEXITCODE -ne 0) { throw "企业数据库初始化失败" }

Write-Host "完成。账号将在应用首次启动时由 UserDataInitializer 写入："
Write-Host "  admin / admin (role=admin)"
Write-Host "如已有库需增量升级，见 resources/database/README.md"
