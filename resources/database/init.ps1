# 初始化独立数据库 myagent（Docker 容器名默认 postgres）
# 新库会执行基础 schema + v0.4.0 至 v0.10.0 全部增量脚本（含字段注释）。
param(
    [string]$Container = "postgres",
    [string]$PgUser = "postgres",
    [string]$DbName = "myagent"
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

function Invoke-PsqlFile {
    param([string]$RelativePath, [string]$Database)
    $fullPath = Join-Path $ScriptDir $RelativePath
    if (-not (Test-Path -LiteralPath $fullPath)) {
        throw "SQL 文件不存在: $fullPath"
    }
    Write-Host "  -> $RelativePath"
    Get-Content $fullPath -Raw -Encoding utf8 | docker exec -i $Container psql -U $PgUser -d $Database -v ON_ERROR_STOP=1
    if ($LASTEXITCODE -ne 0) { throw "执行失败: $RelativePath" }
}

Write-Host "==> 检查/创建数据库 $DbName"
$raw = docker exec $Container psql -U $PgUser -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='$DbName'" 2>$null
$exists = if ($null -eq $raw) { "" } else { "$raw".Trim() }
if ($exists -ne "1") {
    docker exec $Container psql -U $PgUser -d postgres -c "CREATE DATABASE $DbName"
    Write-Host "已创建 $DbName"
} else {
    Write-Host "数据库 $DbName 已存在"
}

Write-Host "==> 执行基础 schema 与占位数据"
Invoke-PsqlFile "01_schema.sql" $DbName
Invoke-PsqlFile "02_init_data.sql" $DbName

$upgrades = @(
    "upgrades\v0.4.0\01_enterprise.sql",
    "upgrades\v0.5.0\01_platform_rbac.sql",
    "upgrades\v0.5.0\01_erp_pending_action.sql",
    "upgrades\v0.5.0\02_platform_rbac_seed.sql",
    "upgrades\v0.6.0\01_workflow_runtime.sql",
    "upgrades\v0.6.0\01_mcp_catalog.sql",
    "upgrades\v0.6.0\02_mcp_intent_binding.sql",
    "upgrades\v0.6.0\02_agent_admin_permissions.sql",
    "upgrades\v0.6.0\03_route_binding_admin.sql",
    "upgrades\v0.6.0\04_unified_audit.sql",
    "upgrades\v0.7.0\01_reasoning_trace.sql",
    "upgrades\v0.7.0\02_employee_self_and_hr_policy_intents.sql",
    "upgrades\v0.8.0\01_conversation_workflow_state.sql",
    "upgrades\v0.8.0\02_capability_intent_examples.sql",
    "upgrades\v0.9.0\01_workflow_process_source.sql",
    "upgrades\v0.9.0\02_mcp_tool_contract.sql",
    "upgrades\v0.9.0\03_workflow_version_package.sql",
    "upgrades\v0.9.0\04_workflow_execution_state.sql",
    "upgrades\v0.9.0\05_workflow_confirmation.sql",
    "upgrades\v0.9.0\06_workflow_release_gate.sql",
    "upgrades\v0.10.0\01_runtime_tool_catalog.sql",
    "upgrades\v0.10.0\02_column_comments.sql"
)

Write-Host "==> 执行增量升级（可重复执行）"
foreach ($item in $upgrades) {
    Invoke-PsqlFile $item $DbName
}

Write-Host ""
Write-Host "完成。账号将在应用首次启动时由 UserDataInitializer 写入："
Write-Host "  admin / admin          平台管理员"
Write-Host "  demo / user123         普通用户"
Write-Host "  employee / Enterprise123!  员工（enterprise profile）"
Write-Host "  manager  / Enterprise123!  主管"
Write-Host "  hr       / Enterprise123!  人事"
Write-Host "独立 ERP 库 internal_erp 请见 ../../internal-erp 与 deploy/docker-compose.yml"
Write-Host "表用途说明见 README.md"
